package kfusion.pipeline;

import java.io.PrintStream;
import kfusion.TornadoModel;
import kfusion.devices.Device;
import kfusion.tornado.algorithms.Integration;
import kfusion.tornado.algorithms.IterativeClosestPoint;
import kfusion.tornado.algorithms.Raycast;
import tornado.collections.graphics.GraphicsMath;
import tornado.collections.graphics.ImagingOps;
import tornado.collections.graphics.Renderer;
import tornado.collections.matrix.MatrixFloatOps;
import tornado.collections.matrix.MatrixMath;
import tornado.collections.types.*;
import tornado.common.Tornado;
import tornado.common.enums.Access;
import tornado.drivers.opencl.OCLKernelConfig;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.PrebuiltTask;
import tornado.runtime.api.TaskGraph;
import tornado.runtime.api.TaskUtils;

import static tornado.collections.types.Float4.mult;
import static tornado.common.RuntimeUtilities.elapsedTimeInSeconds;
import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import static tornado.runtime.api.TaskUtils.createTask;

public class TornadoBenchmarkPipeline extends AbstractPipeline<TornadoModel> {

    private Float3 initialPosition;

    private TaskGraph preprocessingGraph;
    private TaskGraph estimatePoseGraph;
    private TaskGraph trackingPyramid[];
    private TaskGraph integrateGraph;
    private TaskGraph raycastGraph;
    private TaskGraph renderGraph;

    private Matrix4x4Float[] scaledInvKs;
    private Matrix4x4Float pyramidPose;

    private float[] icpResultIntermediate1;
    private float[] icpResultIntermediate2;
    private float[] icpResult;

    private int cus;

    private final PrintStream out;

    public TornadoBenchmarkPipeline(TornadoModel config, PrintStream out) {
        super(config);
        this.out = out;
        initialPosition = new Float3();
    }

    private static int roundToWgs(int value, int wgs) {
        final int numWgs = value / wgs;
        return numWgs * wgs;
    }

    @Override
    public void execute() {
        if (config.getDevice() != null) {
            out.println("frame\tacquisition\tpreprocessing\ttracking\tintegration\traycasting\trendering\tcomputation\ttotal    \tX          \tY          \tZ         \ttracked   \tintegrated");

            final long[] timings = new long[7];

            timings[0] = System.nanoTime();
            boolean haveDepthImage = depthCamera.pollDepth(depthImageInput);
            videoCamera.skipVideoFrame();
//			@SuppressWarnings("unused")
//			boolean haveVideoImage = videoCamera.pollVideo(videoImageInput);

            // read all frames
            while (haveDepthImage) {

                timings[1] = System.nanoTime();
                preprocessing();
                timings[2] = System.nanoTime();

                boolean hasTracked = estimatePose();

                timings[3] = System.nanoTime();

                final boolean doIntegrate = (hasTracked && frames % integrationRate == 0) || frames <= 3;
                if (doIntegrate) {
                    integrate();
                }

                timings[4] = System.nanoTime();

                final boolean doUpdate = frames > 2;

                if (doUpdate) {
                    updateReferenceView();
                }

                timings[5] = System.nanoTime();

                if (frames % renderingRate == 0) {
                    renderGraph.schedule().waitOn();
                }

                timings[6] = System.nanoTime();
                final Float3 currentPos = currentView.getPose().column(3).asFloat3();
                final Float3 pos = Float3.sub(currentPos, initialPosition);

                out
                        .printf("%d\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%d\t%d\n", frames,
                                elapsedTimeInSeconds(timings[0], timings[1]),
                                elapsedTimeInSeconds(timings[1], timings[2]),
                                elapsedTimeInSeconds(timings[2], timings[3]),
                                elapsedTimeInSeconds(timings[3], timings[4]),
                                elapsedTimeInSeconds(timings[4], timings[5]),
                                elapsedTimeInSeconds(timings[5], timings[6]),
                                elapsedTimeInSeconds(timings[1], timings[5]),
                                elapsedTimeInSeconds(timings[0], timings[6]),
                                pos.getX(), pos.getY(), pos.getZ(),
                                (hasTracked) ? 1 : 0, (doIntegrate) ? 1 : 0);

                frames++;

                timings[0] = System.nanoTime();
                haveDepthImage = depthCamera.pollDepth(depthImageInput);
                videoCamera.skipVideoFrame();
//              haveVideoImage = videoCamera.pollVideo(videoImageInput);
            }

            if (config.printKernels()) {
                preprocessingGraph.dumpProfiles();
                estimatePoseGraph.dumpProfiles();
                for (TaskGraph trackingPyramid1 : trackingPyramid) {
                    trackingPyramid1.dumpProfiles();
                }
                integrateGraph.dumpProfiles();
                raycastGraph.dumpProfiles();
                renderGraph.dumpProfiles();
            }

        }
    }

    @Override
    public void configure(Device device) {
        super.configure(device);

        initialPosition = Float3.mult(config.getOffset(), volumeDims);
        frames = 0;

        info("initial offset: %s", initialPosition.toString("%.2f,.2f,.2f"));

        /**
         * Tornado tasks
         */
        final OCLDeviceMapping deviceMapping = (OCLDeviceMapping) config.getTornadoDevice();
        info("mapping onto %s\n", deviceMapping.toString());

        final long localMemSize = deviceMapping.getDevice().getLocalMemorySize();
        final float fraction = Float.parseFloat(Tornado.getProperty("kfusion.reduce.fraction", "1.0"));
        cus = (int) (deviceMapping.getDevice().getMaxComputeUnits() * fraction);
        final int maxBinsPerResource = (int) localMemSize / ((32 * 4) + 24);
        final int maxBinsPerCU = roundToWgs(maxBinsPerResource, 128);

        final int maxwgs = maxBinsPerCU * cus;

        info("local mem size   : %s\n", humanReadableByteCount(localMemSize, false));
        info("num compute units: %d\n", cus);
        info("max bins per cu  : %d\n", maxBinsPerCU);

        pyramidPose = new Matrix4x4Float();
        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();
        icpResult = new float[32];

        final Matrix4x4Float scenePose = sceneView.getPose();

        //@formatter:off
        preprocessingGraph = new TaskGraph()
                .streamIn(depthImageInput)
                .add(ImagingOps::mm2metersKernel, scaledDepthImage, depthImageInput, scalingFactor)
                .add(ImagingOps::bilateralFilter, pyramidDepths[0], scaledDepthImage, gaussian, eDelta, radius)
                .mapAllTo(deviceMapping);
        //@formatter:on

        final int iterations = pyramidIterations.length;
        scaledInvKs = new Matrix4x4Float[iterations];
        for (int i = 0; i < iterations; i++) {
            final Float4 cameraDup = mult(
                    scaledCamera, 1f / (1 << i));
            scaledInvKs[i] = new Matrix4x4Float();
            GraphicsMath.getInverseCameraMatrix(cameraDup, scaledInvKs[i]);
        }

        estimatePoseGraph = new TaskGraph();

        for (int i = 1; i < iterations; i++) {
            //@formatter:off
            estimatePoseGraph
                    .add(ImagingOps::resizeImage6, pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2);
            //@formatter:on
        }

        for (int i = 0; i < iterations; i++) {
            //@formatter:off
            estimatePoseGraph
                    .add(GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i])
                    .add(GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]);
            //@formatter:on
        }

        estimatePoseGraph
                .streamIn(projectReference)
                .mapAllTo(deviceMapping);

        if (config.useCustomReduce()) {
            icpResultIntermediate1 = new float[cus * 32];
        } else if (config.useSimpleReduce()) {
            icpResultIntermediate1 = new float[config.getReductionSize() * 32];
            icpResultIntermediate2 = new float[32 * 32];
        }

        trackingPyramid = new TaskGraph[iterations];
        for (int i = 0; i < iterations; i++) {

            final CompilableTask trackPose = createTask(IterativeClosestPoint::trackPose,
                    pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                    referenceView.getVerticies(), referenceView.getNormals(), pyramidPose,
                    projectReference, distanceThreshold, normalThreshold);

            //@formatter:off
            trackingPyramid[i] = new TaskGraph()
                    .streamIn(pyramidPose)
                    .add(trackPose);

            if (config.useCustomReduce()) {
                final ImageFloat8 result = pyramidTrackingResults[i];
                final int numElements = result.X() * result.Y();

                final int numWgs = Math.min(roundToWgs(numElements / cus, 128), maxwgs);

                final PrebuiltTask customMapReduce = TaskUtils.createTask(
                        deviceMapping.getDeviceContext().needsBump() ? "optMapReduceBump" : "optMapReduce",
                        "./opencl/optMapReduce.cl",
                        new Object[]{icpResultIntermediate1, result, result.X(), result.Y()},
                        new Access[]{Access.WRITE, Access.READ, Access.READ, Access.READ},
                        deviceMapping,
                        new int[]{numWgs});

                final OCLKernelConfig kernelConfig = OCLKernelConfig.create(customMapReduce.meta());
                kernelConfig.getGlobalWork()[0] = maxwgs;
                kernelConfig.getLocalWork()[0] = maxBinsPerCU;

                trackingPyramid[i].add(customMapReduce)
                        .streamOut(icpResultIntermediate1);
            } else if (config.useSimpleReduce()) {
                trackingPyramid[i].add(IterativeClosestPoint::mapReduce, icpResultIntermediate1, pyramidTrackingResults[i])
                        //.add(IterativeClosestPoint::reduceIntermediate, icpResultIntermediate2, icpResultIntermediate1)
                        .streamOut(icpResultIntermediate1);
            } else {
                trackingPyramid[i].streamOut(pyramidTrackingResults[i]);
            }
            trackingPyramid[i].mapAllTo(deviceMapping);
            //@formatter:on
        }

        //@formatter:off
        integrateGraph = new TaskGraph()
                .streamIn(invTrack)
                .add(Integration::integrate, scaledDepthImage, invTrack, K, volumeDims, volume, mu, maxWeight)
                .mapAllTo(deviceMapping);
        //@formatter:on

        final ImageFloat3 verticies = referenceView.getVerticies();
        final ImageFloat3 normals = referenceView.getNormals();

        CompilableTask raycast = createTask(Raycast::raycast, verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep, smallStep);
//

        //@formatter:off
        raycastGraph = new TaskGraph()
                .streamIn(referencePose)
                .add(raycast)
                .mapAllTo(deviceMapping);
        //@formatter:on

        final CompilableTask renderVolume = createTask(Renderer::renderVolume,
                renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep,
                largeStep, light, ambient);

        //@formatter:off
        renderGraph = new TaskGraph()
                .streamIn(scenePose)
                .add(Renderer::renderTrack, renderedTrackingImage, pyramidTrackingResults[0])
                //BUG need to fix render depth issue
                // .add(Renderer::renderDepth,renderedDepthImage, filteredDepthImage, nearPlane, farPlane)
                .add(renderVolume)
                .mapAllTo(deviceMapping);
        //@formatter:on

        preprocessingGraph.warmup();
        estimatePoseGraph.warmup();
        for (TaskGraph trackingPyramid1 : trackingPyramid) {
            trackingPyramid1.warmup();
        }
        integrateGraph.warmup();
        raycastGraph.warmup();
        renderGraph.warmup();
    }

    @Override
    protected void preprocessing() {
        preprocessingGraph.schedule().waitOn();
    }

    @Override
    protected void integrate() {
        invTrack.set(currentView.getPose());
        MatrixFloatOps.inverse(invTrack);

        integrateGraph.schedule().waitOn();
    }

    @Override
    protected boolean estimatePose() {

        invReferencePose.set(referenceView.getPose());
        MatrixFloatOps.inverse(invReferencePose);
        MatrixMath.sgemm(K, invReferencePose, projectReference);

        estimatePoseGraph.schedule().waitOn();

        // perform ICP
        pyramidPose.set(currentView.getPose());
        for (int level = pyramidIterations.length - 1; level >= 0; level--) {
            for (int i = 0; i < pyramidIterations[level]; i++) {
                trackingPyramid[level].schedule().waitOn();

                final boolean updated;
                if (config.useCustomReduce()) {
                    for (int k = 1; k < cus; k++) {
                        final int index = k * 32;
                        for (int j = 0; j < 32; j++) {
                            icpResultIntermediate1[j] += icpResultIntermediate1[index + j];
                        }
                    }
                    trackingResult.resultImage = pyramidTrackingResults[level];
                    updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResultIntermediate1, pyramidPose, 1e-5f);
                } else if (config.useSimpleReduce()) {
                    IterativeClosestPoint.reduceIntermediate(icpResult, icpResultIntermediate1);
                    trackingResult.resultImage = pyramidTrackingResults[level];
                    updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResult, pyramidPose, 1e-5f);
                } else {
                    updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, pyramidTrackingResults[level], pyramidPose, 1e-5f);
                }

                pyramidPose.set(trackingResult.getPose());

                if (updated) {
                    break;
                }

            }
        }

        // if the tracking result meets our constraints, update the current view with the estimated
        // pose
        boolean hasTracked = trackingResult.getRSME() < RSMEThreshold
                && trackingResult.getTracked(scaledInputSize.getX() * scaledInputSize.getY()) >= trackingThreshold;
        if (hasTracked) {
            currentView.getPose().set(trackingResult.getPose());
        }
        return hasTracked;
    }

    @Override
    public void updateReferenceView() {
        referenceView.getPose().set(currentView.getPose());

        // convert the tracked pose into correct co-ordinate system for
        // raycasting
        // which system (homogeneous co-ordinates? or virtual image?)
        MatrixMath.sgemm(currentView.getPose(), scaledInvK, referencePose);
        raycastGraph.schedule().waitOn();
    }

}
