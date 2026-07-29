/*
 *  This file is part of Tornado-KFusion: A Java version of the KFusion computer vision
 *  algorithm running on TornadoVM.
 *  URL: https://github.com/beehive-lab/kfusion-tornadovm
 *
 *  Copyright (c) 2013-2019, 2024, APT Group, Department of Computer Science,
 *  The University of Manchester
 *
 *  This work is partially supported by EPSRC grants Anyscale EP/L000725/1,
 *  PAMELA EP/K008730/1, and EU Horizon 2020 E2Data 780245.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package kfusion.tornado.pipeline;

import static kfusion.tornado.algorithms.GraphicsMath.getInverseCameraMatrix;
import static uk.ac.manchester.tornado.api.utils.TornadoAPIUtils.elapsedTimeInSeconds;
import static uk.ac.manchester.tornado.api.utils.TornadoAPIUtils.humanReadableByteCount;

import java.io.PrintStream;

import kfusion.java.devices.Device;
import kfusion.java.pipeline.AbstractPipeline;
import kfusion.tornado.algorithms.GraphicsMath;
import kfusion.tornado.algorithms.ImagingOps;
import kfusion.tornado.algorithms.Integration;
import kfusion.tornado.algorithms.IterativeClosestPoint;
import kfusion.tornado.algorithms.Raycast;
import kfusion.tornado.algorithms.Renderer;
import kfusion.tornado.common.Nvtx;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.matrix.MatrixFloatOps;
import uk.ac.manchester.tornado.matrix.MatrixMath;

public class TornadoBenchmarkPipeline extends AbstractPipeline<TornadoModel> {

    private Float3 initialPosition;

    /**
     * All task-graphs live in ONE execution plan: a plan owns the device buffer pool, so objects
     * produced by one graph are only visible to another graph of the SAME plan. With one plan per
     * graph (as this pipeline used to do) every consumer re-uploaded stale host data and tracking
     * silently produced a zero trajectory.
     */
    private TornadoExecutionPlan plan;

    private GridScheduler gridScheduler;
    private boolean captureCUDAGraphs;

    private static final int GRAPH_PREPROC = 0;
    private int graphIcpFirst;
    private int graphIntegrate;
    private int graphRaycast;
    private int graphRenderTrack;
    private int graphRender;

    private Matrix4x4Float[] scaledInvKs;
    private Matrix4x4Float pyramidPose;

    private FloatArray icpResultIntermediate1;
    private FloatArray icpGroupPartials;
    private FloatArray icpResult;
    private int icpReduceGroups;
    private int icpReduceChunk;

    /**
     * ICP correspondences, {@link IterativeClosestPoint#TRACK_STRIDE} floats per pixel and one array
     * per pyramid level. Flat arrays rather than ImageFloat8: the CUDA backend has no float8.
     */
    private FloatArray[] trackingResults;
    private int[] trackingWidth;
    private int[] trackingHeight;

    private int cus;
    private int pyramidLevels;
    private boolean preprocMapped;
    private boolean integrateMapped;
    private boolean raycastMapped;
    private boolean trackMapped;

    private final PrintStream out;

    public static final float ICP_THRESHOLD = 1e-5f;

    private static final String HEAD_BENCHMARK = "frame\tacquisition\tpreprocessing\ttracking\tintegration\traycasting\trendering\tcomputation\ttotal    \tX          \tY          \tZ         \ttracked   \tintegrated";

    public TornadoBenchmarkPipeline(TornadoModel config, PrintStream out) {
        super(config);
        this.out = out;
        initialPosition = new Float3();
    }

    @Override
    public void execute() {
        if (config.getDevice() != null) {

            out.println(HEAD_BENCHMARK);

            final long[] timings = new long[7];

            final int maxFrames = config.getMaxFrames();

            timings[0] = System.nanoTime();
            boolean haveDepthImage = depthCamera.pollDepth(depthImageInput);
            videoCamera.skipVideoFrame();

            // read all frames
            while (haveDepthImage && frames < maxFrames) {

                Nvtx.push("frame " + frames);

                timings[1] = System.nanoTime();
                Nvtx.push("preprocessing");
                preprocessing();
                Nvtx.pop();
                timings[2] = System.nanoTime();

                Nvtx.push("tracking");
                boolean hasTracked = estimatePose();
                Nvtx.pop();

                timings[3] = System.nanoTime();

                final boolean doIntegrate = (hasTracked && frames % integrationRate == 0) || frames <= 3;
                if (doIntegrate) {
                    Nvtx.push("integration");
                    integrate();
                    Nvtx.pop();
                }

                timings[4] = System.nanoTime();

                final boolean doUpdate = frames > 2;

                if (doUpdate) {
                    Nvtx.push("raycasting");
                    updateReferenceView();
                    Nvtx.pop();
                }

                timings[5] = System.nanoTime();

                if (frames % renderingRate == 0) {
                    Nvtx.push("rendering");
                    runGraph(graphRenderTrack);
                    runGraph(graphRender);
                    Nvtx.pop();
                }

                timings[6] = System.nanoTime();
                Nvtx.pop(); // frame
                final Float3 currentPos = currentView.getPose().column(3).asFloat3();
                final Float3 pos = Float3.sub(currentPos, initialPosition);

                out.printf("%d\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%d\t%d\n", frames, elapsedTimeInSeconds(timings[0], timings[1]), elapsedTimeInSeconds(timings[1], timings[2]),
                        elapsedTimeInSeconds(timings[2], timings[3]), elapsedTimeInSeconds(timings[3], timings[4]), elapsedTimeInSeconds(timings[4], timings[5]),
                        elapsedTimeInSeconds(timings[5], timings[6]), elapsedTimeInSeconds(timings[1], timings[5]), elapsedTimeInSeconds(timings[0], timings[6]), pos.getX(), pos.getY(), pos.getZ(),
                        (hasTracked) ? 1 : 0, (doIntegrate) ? 1 : 0);
                frames++;
                timings[0] = System.nanoTime();
                Nvtx.push("acquisition");
                haveDepthImage = depthCamera.pollDepth(depthImageInput);
                videoCamera.skipVideoFrame();
                Nvtx.pop();
            }
        }
    }

    @Override
    public void configure(Device device) {
        super.configure(device);

        initialPosition = Float3.mult(config.getOffset(), volumeDims);
        frames = 0;

        info("initial offset: %s", initialPosition.toString("%.2f,%.2f,%.2f"));

        /**
         * Tornado tasks
         */
        final TornadoDevice tornadoDevice = config.getTornadoDevice();
        info("mapping onto %s\n", tornadoDevice.toString());

        if (config.useNvtx()) {
            Nvtx.enable(tornadoDevice);
            info("NVTX ranges      : %s\n", Nvtx.isEnabled() ? "enabled" : "unsupported on this device");
        }

        final long localMemSize = tornadoDevice.getPhysicalDevice().getDeviceLocalMemorySize();
        cus = tornadoDevice.getPhysicalDevice().getDeviceMaxComputeUnits();

        info("local mem size   : %s\n", humanReadableByteCount(localMemSize, false));
        info("num compute units: %d\n", cus);

        pyramidPose = new Matrix4x4Float();
        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();
        icpResult = new FloatArray(32);

        trackingResults = new FloatArray[pyramidIterations.length];
        trackingWidth = new int[pyramidIterations.length];
        trackingHeight = new int[pyramidIterations.length];
        for (int i = 0; i < pyramidIterations.length; i++) {
            trackingWidth[i] = pyramidTrackingResults[i].X();
            trackingHeight[i] = pyramidTrackingResults[i].Y();
            trackingResults[i] = new FloatArray(trackingWidth[i] * trackingHeight[i] * IterativeClosestPoint.TRACK_STRIDE);
        }

        final Matrix4x4Float scenePose = sceneView.getPose();

        // ---------------------------------------------------------------------------------------
        // Graph 0: preprocessing + the whole depth/vertex/normal pyramid.
        // Merged from the old "pp" and "estimatePose" graphs: the pyramid images are produced and
        // consumed here, so they never need to cross a graph boundary.
        // ---------------------------------------------------------------------------------------
        final int iterations = pyramidIterations.length;
        scaledInvKs = new Matrix4x4Float[iterations];
        for (int i = 0; i < iterations; i++) {
            final Float4 cameraDup = Float4.mult(scaledCamera, 1f / (1 << i));
            scaledInvKs[i] = new Matrix4x4Float();
            scaledInvKs[i].clear();
            getInverseCameraMatrix(cameraDup, scaledInvKs[i]);
        }

        final TaskGraph preprocGraph = new TaskGraph("preproc") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, depthImageInput) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, scaledDepthImage, pyramidDepths[0], gaussian) //
                .task("mm2meters", ImagingOps::mm2metersKernel, scaledDepthImage, depthImageInput, scalingFactor) //
                .task("bilateralFilter", ImagingOps::bilateralFilter, pyramidDepths[0], scaledDepthImage, gaussian, eDelta, radius);

        for (int i = 1; i < iterations; i++) {
            preprocGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, pyramidDepths[i]) //
                    .task("resizeImage" + i, ImagingOps::resizeImage6, pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2);
        }

        for (int i = 0; i < iterations; i++) {
            preprocGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, pyramidVerticies[i], pyramidNormals[i], scaledInvKs[i]) //
                    .task("d2v" + i, GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i]) //
                    .task("v2n" + i, GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]);
        }

        // keep the pyramid and the scaled depth image device-resident for the icp/integrate graphs
        preprocGraph.persistOnDevice(scaledDepthImage);
        for (int i = 0; i < iterations; i++) {
            preprocGraph.persistOnDevice(pyramidVerticies[i], pyramidNormals[i]);
        }

        if (Boolean.getBoolean("kfusion.debug.images")) {
            preprocGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, scaledDepthImage, pyramidDepths[0], pyramidVerticies[0], pyramidNormals[0]);
        }

        // ---------------------------------------------------------------------------------------
        // Graphs 1..n: one ICP graph per pyramid level, executed pyramidIterations[level] times.
        // ---------------------------------------------------------------------------------------
        if (config.useSimpleReduce()) {
            icpResultIntermediate1 = new FloatArray(config.getReductionSize() * 32);
            icpReduceChunk = Math.max(1, config.getReductionSize() / config.getReduceGroups());
            icpReduceGroups = Math.max(1, config.getReductionSize() / icpReduceChunk);
            icpGroupPartials = new FloatArray(icpReduceGroups * 32);
        }

        final ImageFloat3 referenceVerticies = referenceView.getVerticies();
        final ImageFloat3 referenceNormals = referenceView.getNormals();

        final TaskGraph[] icpGraphs = new TaskGraph[iterations];
        for (int i = 0; i < iterations; i++) {
            icpGraphs[i] = new TaskGraph("icp" + i) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, pyramidPose, projectReference) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, trackingResults[i]) //
                    .consumeFromDevice("preproc", pyramidVerticies[i], pyramidNormals[i]) //
                    .consumeFromDevice("raycast", referenceVerticies, referenceNormals) //
                    .task("track" + i, IterativeClosestPoint::trackPose, //
                            trackingResults[i], trackingWidth[i], trackingHeight[i], pyramidVerticies[i], pyramidNormals[i], //
                            referenceVerticies, referenceNormals, pyramidPose, //
                            projectReference, distanceThreshold, normalThreshold);

            if (config.useSimpleReduce()) {
                icpGraphs[i].transferToDevice(DataTransferMode.FIRST_EXECUTION, icpResultIntermediate1, icpResult) //
                        .task("mapreduce" + i, IterativeClosestPoint::mapReduce, icpResultIntermediate1, trackingResults[i], trackingWidth[i] * trackingHeight[i]);
                if (config.useTwoStageReduce()) {
                    // finish the reduction on the device: 32 floats come back instead of reductionSize * 32
                    icpGraphs[i].transferToDevice(DataTransferMode.FIRST_EXECUTION, icpGroupPartials) //
                            .task("reducepartials" + i, IterativeClosestPoint::reducePartials, icpGroupPartials, icpResultIntermediate1, icpReduceChunk) //
                            .task("reducefinal" + i, IterativeClosestPoint::reduceFinal, icpResult, icpGroupPartials, icpReduceGroups) //
                            .transferToHost(DataTransferMode.EVERY_EXECUTION, icpResult);
                } else {
                    icpGraphs[i].transferToHost(DataTransferMode.EVERY_EXECUTION, icpResultIntermediate1);
                }
            } else {
                icpGraphs[i].transferToHost(DataTransferMode.EVERY_EXECUTION, trackingResults[i]);
            }
            // renderTrack reads the finest level; never copy the tracking image back to the host
            icpGraphs[i].persistOnDevice(trackingResults[i]);
        }

        // ---------------------------------------------------------------------------------------
        // Graphs n+1..n+4: integrate, raycast and the two render graphs.
        // ---------------------------------------------------------------------------------------
        final TaskGraph integrateGraph = new TaskGraph("integrate") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, invTrack) //
                .consumeFromDevice("preproc", scaledDepthImage) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, K, volumeDims, volume) //
                .task("integrate", Integration::integrate, scaledDepthImage, invTrack, K, volumeDims, volume, mu, maxWeight) //
                .persistOnDevice(volume);
        if (Boolean.getBoolean("kfusion.debug.images")) {
            integrateGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, volume);
        }

        final TaskGraph raycastGraph = new TaskGraph("raycast") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, referencePose) //
                .consumeFromDevice("integrate", volume) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, referenceVerticies, referenceNormals, volumeDims) //
                .task("raycast", Raycast::raycast, referenceVerticies, referenceNormals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep, smallStep) //
                .persistOnDevice(referenceVerticies, referenceNormals);
        if (Boolean.getBoolean("kfusion.debug.images")) {
            raycastGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, referenceVerticies, referenceNormals);
        }

        final TaskGraph renderTrackGraph = new TaskGraph("renderTrack") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, renderedTrackingImage) //
                .consumeFromDevice("icp0", trackingResults[0]) //
                .task("renderTrack", Renderer::renderTrack, renderedTrackingImage, trackingResults[0], trackingWidth[0], trackingHeight[0]) //
                .persistOnDevice(renderedTrackingImage);

        final TaskGraph renderGraph = new TaskGraph("render") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scenePose) //
                .consumeFromDevice("integrate", volume) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, renderedScene, volumeDims, light, ambient) //
                .task("renderVolume", Renderer::renderVolume, renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep, largeStep, light, ambient) //
                .persistOnDevice(renderedScene);

        // ---------------------------------------------------------------------------------------
        // One execution plan over every graph; each phase runs with plan.withGraph(index).
        // ---------------------------------------------------------------------------------------
        final ImmutableTaskGraph[] graphs = new ImmutableTaskGraph[4 + iterations + 1];
        int index = 0;
        graphs[index++] = preprocGraph.snapshot();
        graphIcpFirst = index;
        for (int i = 0; i < iterations; i++) {
            graphs[index++] = icpGraphs[i].snapshot();
        }
        graphIntegrate = index;
        graphs[index++] = integrateGraph.snapshot();
        graphRaycast = index;
        graphs[index++] = raycastGraph.snapshot();
        graphRenderTrack = index;
        graphs[index++] = renderTrackGraph.snapshot();
        graphRender = index;
        graphs[index++] = renderGraph.snapshot();

        pyramidLevels = iterations;

        // Explicit thread-block shapes. The CUDA default is 16x16 for a 2D domain, which splits every
        // warp across two image rows; (32, y) keeps a warp on one contiguous row. Ray-marching kernels
        // get shorter blocks so the divergent tail load-balances.
        gridScheduler = new GridScheduler();
        addWorkerGrid2D("preproc.mm2meters", scaledDepthImage.X(), scaledDepthImage.Y(), 32, 8);
        addWorkerGrid2D("preproc.bilateralFilter", pyramidDepths[0].X(), pyramidDepths[0].Y(), 32, 16);
        for (int i = 1; i < iterations; i++) {
            addWorkerGrid2D("preproc.resizeImage" + i, pyramidDepths[i].X(), pyramidDepths[i].Y(), 32, 8);
        }
        for (int i = 0; i < iterations; i++) {
            addWorkerGrid2D("preproc.d2v" + i, pyramidVerticies[i].X(), pyramidVerticies[i].Y(), 32, 8);
            addWorkerGrid2D("preproc.v2n" + i, pyramidNormals[i].X(), pyramidNormals[i].Y(), 32, 8);
            addWorkerGrid2D("icp" + i + ".track" + i, trackingWidth[i], trackingHeight[i], 32, 8);
            if (config.useSimpleReduce()) {
                addWorkerGrid1D("icp" + i + ".mapreduce" + i, config.getReductionSize(), 128);
                if (config.useTwoStageReduce()) {
                    addWorkerGrid1D("icp" + i + ".reducepartials" + i, icpReduceGroups * 32, 128);
                    addWorkerGrid1D("icp" + i + ".reducefinal" + i, 32, 32);
                }
            }
        }
        addWorkerGrid2D("integrate.integrate", volume.X(), volume.Y(), 32, 8);
        addWorkerGrid2D("raycast.raycast", referenceVerticies.X(), referenceVerticies.Y(), 32, 4);
        addWorkerGrid2D("renderTrack.renderTrack", trackingWidth[0], trackingHeight[0], 32, 8);
        addWorkerGrid2D("render.renderVolume", renderedScene.X(), renderedScene.Y(), 32, 4);

        plan = new TornadoExecutionPlan(graphs);
        if (config.useGridScheduler()) {
            plan.withGridScheduler(gridScheduler);
            info("grid scheduler   : enabled\n");
        }
        // Capture has to be requested BEFORE a graph's first execution: TornadoVM generates the
        // bytecode - including the capture region - once and caches it per device, so a later request is
        // silently ignored. Allocation and copy-in bytecodes are emitted outside the region by the graph
        // compiler, so the warm-up below is what performs the capture.
        final String cudaGraphScope = config.getCUDAGraphScope();
        if ("all".equalsIgnoreCase(cudaGraphScope)) {
            plan.withAllGraphs().withCUDAGraph();
            captureCUDAGraphs = true;
            info("CUDA graphs      : enabled\n");
        }
        // NOTE: withPreCompilation() must NOT be used here. It runs every graph in isolation, which
        // leaves each graph with its own device buffers, so the pyramid/reference images produced by
        // one graph are invisible to the next and tracking silently degenerates.
        // Execute every graph once before the frame loop. This is what captures the CUDA graphs when
        // capture is enabled, and it also gets JIT compilation and the first-execution allocations out
        // of the timed loop. GPULlama3 does the same thing through forceCopyInReadOnlyData().
        // Integration is a no-op here because depthImageInput is still zero and integrate() skips pixels
        // with zero depth. Raycasting is deliberately left out: it would overwrite the reference view
        // with INVALID before the first frame, which perturbs the first tracked frames and shifts the
        // whole trajectory. Its one-off first execution happens in-loop at frame 3 instead.
        if (config.useWarmUp()) {
            runGraph(GRAPH_PREPROC);
            for (int i = 0; i < iterations; i++) {
                runGraph(graphIcpFirst + i);
            }
            runGraph(graphIntegrate);
            runGraph(graphRenderTrack);
            runGraph(graphRender);
            info("warm-up          : every graph executed once\n");
        }

    }

    private void addWorkerGrid2D(String taskName, int globalX, int globalY, int localX, int localY) {
        final WorkerGrid grid = new WorkerGrid2D(globalX, globalY);
        grid.setLocalWork(Math.min(localX, globalX), Math.min(localY, globalY), 1);
        gridScheduler.addWorkerGrid(taskName, grid);
    }

    private void addWorkerGrid1D(String taskName, int globalX, int localX) {
        final WorkerGrid grid = new WorkerGrid1D(globalX);
        grid.setLocalWork(Math.min(localX, globalX), 1, 1);
        gridScheduler.addWorkerGrid(taskName, grid);
    }

    /** Runs a single task-graph of the shared execution plan. */
    private void runGraph(int graphIndex) {
        // The grid scheduler is registered once, at plan construction: re-registering it on every
        // execute() walks every task of every graph again, which is pure host overhead in the ICP loop.
        final TornadoExecutionPlan graphPlan = plan.withGraph(graphIndex);
        if (captureCUDAGraphs) {
            // withGraph() re-selects the graph, so the capture request has to be restated for the
            // selected graph on every execution - the same way GPULlama3's master plans do it.
            graphPlan.withCUDAGraph();
        }
        graphPlan.execute();
    }

    @Override
    protected void preprocessing() {
        runGraph(GRAPH_PREPROC);
        if (Boolean.getBoolean("kfusion.debug.images")) {
            out.printf("[dbg] frame %d depthIn %s | scaled %s | filtered %s%n", frames, stats(depthImageInput.getArray()), stats(scaledDepthImage.getArray()), stats(pyramidDepths[0].getArray()));
        }
    }

    private static String stats(uk.ac.manchester.tornado.api.types.arrays.FloatArray array) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        double sum = 0;
        int nonZero = 0;
        for (int i = 0; i < array.getSize(); i++) {
            final float value = array.get(i);
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
            if (value != 0f) {
                nonZero++;
            }
        }
        return String.format("min=%.4f max=%.4f mean=%.4f nz=%d/%d", min, max, sum / array.getSize(), nonZero, array.getSize());
    }

    @Override
    protected void integrate() {
        invTrack.set(currentView.getPose());
        MatrixFloatOps.inverse(invTrack);

        runGraph(graphIntegrate);
        if (Boolean.getBoolean("kfusion.debug.images")) {
            int nz = 0;
            for (int z = 0; z < volume.Z(); z++) {
                for (int y = 0; y < volume.Y(); y++) {
                    for (int x = 0; x < volume.X(); x++) {
                        if (volume.get(x, y, z).getX() != 0) {
                            nz++;
                        }
                    }
                }
            }
            out.printf("[dbg] frame %d volume non-zero voxels = %d%n", frames, nz);
        }
    }

    @Override
    protected boolean estimatePose() {

        invReferencePose.set(referenceView.getPose());
        MatrixFloatOps.inverse(invReferencePose);
        MatrixMath.sgemm(K, invReferencePose, projectReference);

        if (Boolean.getBoolean("kfusion.debug.images")) {
            out.printf("[dbg] frame %d depth1 %s | verts0 %s | normals0 %s%n", frames, stats(pyramidDepths[1].getArray()), stats(pyramidVerticies[0].getArray()), stats(pyramidNormals[0].getArray()));
        }

        // perform ICP
        pyramidPose.set(currentView.getPose());
        for (int level = pyramidIterations.length - 1; level >= 0; level--) {
            for (int i = 0; i < pyramidIterations[level]; i++) {
                runGraph(graphIcpFirst + level);

                final boolean updated;
                trackingResult.points = trackingWidth[level] * trackingHeight[level];
                if (config.useSimpleReduce()) {
                    if (!config.useTwoStageReduce()) {
                        IterativeClosestPoint.reduceIntermediate(icpResult, icpResultIntermediate1);
                    }
                    updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResult, pyramidPose, ICP_THRESHOLD);
                } else {
                    updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, trackingResults[level], trackingWidth[level] * trackingHeight[level], pyramidPose, ICP_THRESHOLD);
                }

                if (Boolean.getBoolean("kfusion.debug.images") && !config.useSimpleReduce()) {
                    final java.util.Map<Integer, Integer> histogram = new java.util.TreeMap<>();
                    for (int element = 0; element < trackingWidth[level] * trackingHeight[level]; element++) {
                        histogram.merge((int) trackingResults[level].get((element * IterativeClosestPoint.TRACK_STRIDE) + 7), 1, Integer::sum);
                    }
                    out.printf("[dbg] frame %d level %d status histogram %s%n", frames, level, histogram);
                }

                pyramidPose.set(trackingResult.getPose());

                if (updated) {
                    break;
                }
            }
        }

        // If the tracking result meets our constraints, update the current view
        // with the estimated pose
        boolean hasTracked = (trackingResult.getRSME() < RSMEThreshold) && (trackingResult.getTracked(scaledInputSize.getX() * scaledInputSize.getY()) >= trackingThreshold);
        if (hasTracked) {
            currentView.getPose().set(trackingResult.getPose());
        }
        return true;
    }

    @Override
    public void updateReferenceView() {
        referenceView.getPose().set(currentView.getPose());
        // convert the tracked pose into correct co-ordinate system for
        // raycasting which system (homogeneous co-ordinates? or virtual image?)
        MatrixMath.sgemm(currentView.getPose(), scaledInvK, referencePose);
        runGraph(graphRaycast);
        if (Boolean.getBoolean("kfusion.debug.images")) {
            out.printf("[dbg] frame %d refVerts %s | refNormals %s%n", frames, stats(referenceView.getVerticies().getArray()), stats(referenceView.getNormals().getArray()));
        }
    }

}
