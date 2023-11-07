/*
 *  This file is part of Tornado-KFusion: A Java version of the KFusion computer vision
 *  algorithm running on TornadoVM.
 *  URL: https://github.com/beehive-lab/kfusion-tornadovm
 *
 *  Copyright (c) 2013-2019 APT Group, School of Computer Science,
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

import static uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath.getInverseCameraMatrix;
import static uk.ac.manchester.tornado.api.utils.TornadoUtilities.elapsedTimeInSeconds;
import static uk.ac.manchester.tornado.api.utils.TornadoUtilities.humanReadableByteCount;

import java.io.PrintStream;

import kfusion.java.devices.Device;
import kfusion.java.pipeline.AbstractPipeline;
import kfusion.tornado.algorithms.Integration;
import kfusion.tornado.algorithms.IterativeClosestPoint;
import kfusion.tornado.algorithms.Raycast;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.DRMode;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.graphics.ImagingOps;
import uk.ac.manchester.tornado.api.collections.graphics.Renderer;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat8;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.data.nativetypes.FloatArray;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.matrix.MatrixFloatOps;
import uk.ac.manchester.tornado.matrix.MatrixMath;

public class TornadoBenchmarkPipeline extends AbstractPipeline<TornadoModel> {

    private Float3 initialPosition;

    private TaskGraph preprocessingGraph;
    private TornadoExecutionPlan preprocessingPlan;
    private TaskGraph estimatePoseGraph;
    private TornadoExecutionPlan estimatePosePlan;
    private TaskGraph trackingPyramidGraphs[];
    private TornadoExecutionPlan trackingPyramidPlans[];
    private TaskGraph integrateGraph;
    private TornadoExecutionPlan integratePlan;
    private TaskGraph raycastGraph;
    private TornadoExecutionPlan raycastPlan;
    private TaskGraph renderTrackGraph;
    private TornadoExecutionPlan renderTrackPlan;
    private TaskGraph renderGraph;
    private TornadoExecutionPlan renderPlan;

    private Matrix4x4Float[] scaledInvKs;
    private Matrix4x4Float pyramidPose;

    private FloatArray icpResultIntermediate1;
    private FloatArray icpResult;

    private int cus;

    private final PrintStream out;

    public static final float ICP_THRESHOLD = 1e-5f;

    public static boolean EXECUTE_WITH_PROFILER = Boolean.parseBoolean(System.getProperty("slambench.profiler", "False"));

    private static final String HEAD_BENCHMARK = "frame\tacquisition\tpreprocessing\ttracking\tintegration\traycasting\trendering\tcomputation\ttotal    \tX          \tY          \tZ         \ttracked   \tintegrated";

    private static final Policy policy = Policy.PERFORMANCE;

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

            out.println(HEAD_BENCHMARK);

            final long[] timings = new long[7];

            timings[0] = System.nanoTime();
            boolean haveDepthImage = depthCamera.pollDepth(depthImageInput);
            videoCamera.skipVideoFrame();

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
                    if (EXECUTE_WITH_PROFILER) {
                        renderTrackPlan.withDynamicReconfiguration(policy, DRMode.SERIAL).execute();
                        renderPlan.withDynamicReconfiguration(policy, DRMode.SERIAL).execute();
                    } else {
                        renderTrackPlan.execute();
                        renderPlan.execute();
                    }
                }

                timings[6] = System.nanoTime();
                final Float3 currentPos = currentView.getPose().column(3).asFloat3();
                final Float3 pos = Float3.sub(currentPos, initialPosition);

                out.printf("%d\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%d\t%d\n", frames, elapsedTimeInSeconds(timings[0], timings[1]), elapsedTimeInSeconds(timings[1], timings[2]),
                        elapsedTimeInSeconds(timings[2], timings[3]), elapsedTimeInSeconds(timings[3], timings[4]), elapsedTimeInSeconds(timings[4], timings[5]),
                        elapsedTimeInSeconds(timings[5], timings[6]), elapsedTimeInSeconds(timings[1], timings[5]), elapsedTimeInSeconds(timings[0], timings[6]), pos.getX(), pos.getY(), pos.getZ(),
                        (hasTracked) ? 1 : 0, (doIntegrate) ? 1 : 0);
                frames++;
                timings[0] = System.nanoTime();
                haveDepthImage = depthCamera.pollDepth(depthImageInput);
                videoCamera.skipVideoFrame();
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

        final long localMemSize = tornadoDevice.getPhysicalDevice().getDeviceLocalMemorySize();
        final float fraction = Float.parseFloat(TornadoRuntime.getProperty("kfusion.reduce.fraction", "1.0"));
        cus = (int) (tornadoDevice.getPhysicalDevice().getDeviceMaxComputeUnits() * fraction);
        final int maxBinsPerResource = (int) localMemSize / ((32 * 4) + 24);
        final int maxBinsPerCU = roundToWgs(maxBinsPerResource, 128);

        final int maxwgs = maxBinsPerCU * cus;

        info("local mem size   : %s\n", humanReadableByteCount(localMemSize, false));
        info("num compute units: %d\n", cus);
        info("max bins per cu  : %d\n", maxBinsPerCU);

        pyramidPose = new Matrix4x4Float();
        pyramidPose.clear();
        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();
        icpResult = new FloatArray(32);

        final Matrix4x4Float scenePose = sceneView.getPose();

		preprocessingGraph = new TaskGraph("pp") //
				.transferToDevice(DataTransferMode.EVERY_EXECUTION, depthImageInput) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, scaledDepthImage, pyramidDepths[0], gaussian) //
				.task("mm2meters", ImagingOps::mm2metersKernel, scaledDepthImage, depthImageInput, scalingFactor) //
				.task("bilateralFilter", ImagingOps::bilateralFilter, pyramidDepths[0], scaledDepthImage, gaussian,  eDelta, radius);


        final int iterations = pyramidIterations.length;
        scaledInvKs = new Matrix4x4Float[iterations];
        for (int i = 0; i < iterations; i++) {
            final Float4 cameraDup = Float4.mult(scaledCamera, 1f / (1 << i));
            scaledInvKs[i] = new Matrix4x4Float();
            scaledInvKs[i].clear();
            getInverseCameraMatrix(cameraDup, scaledInvKs[i]);
        }

        estimatePoseGraph = new TaskGraph("estimatePose");
        for (int i = 1; i < iterations; i++) {
            estimatePoseGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, projectReference, pyramidDepths[i], pyramidDepths[i - 1], pyramidVerticies[i], scaledInvKs[i], pyramidNormals[i]);
            estimatePoseGraph.task("resizeImage" + i, ImagingOps::resizeImage6, pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2);
        }

        for (int i = 0; i < iterations; i++) {
            //@formatter:off
			estimatePoseGraph
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, pyramidDepths[i], pyramidVerticies[i], scaledInvKs[i], pyramidNormals[i])
				.task("d2v" + i, GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i])
				.task("v2n" + i, GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]);
			//@formatter:on
        }

        estimatePoseGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, projectReference);

        if (config.useCustomReduce()) {
            icpResultIntermediate1 = new FloatArray(cus * 32);
        } else if (config.useSimpleReduce()) {
            icpResultIntermediate1 = new FloatArray(config.getReductionSize() * 32);
        }

        trackingPyramidGraphs = new TaskGraph[iterations];

        for (int i = 0; i < iterations; i++) {
            //@formatter:off
			trackingPyramidGraphs[i] = new TaskGraph("icp" + i)
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, pyramidPose)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                            referenceView.getVerticies(), referenceView.getNormals(),
                            projectReference, distanceThreshold, normalThreshold)
					.task("track" + i, IterativeClosestPoint::trackPose,
							pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
							referenceView.getVerticies(), referenceView.getNormals(), pyramidPose,
							projectReference, distanceThreshold, normalThreshold);

			if (config.useCustomReduce()) {
				final ImageFloat8 result = pyramidTrackingResults[i];
				final int numElements = result.X() * result.Y();
				final int numWgs = Math.min(roundToWgs(numElements / cus, 128), maxwgs);

				trackingPyramidGraphs[i].prebuiltTask("customReduce" + i,
									tornadoDevice.getDeviceContext().needsBump() ? "optMapReduceBump" : "optMapReduce",
									"./opencl/optMapReduce.cl",
									new Object[]{icpResultIntermediate1, result, result.X(), result.Y()},
									new Access[]{Access.WRITE_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY},
									tornadoDevice,
									new int[]{numWgs})
								  .transferToHost(DataTransferMode.EVERY_EXECUTION, icpResultIntermediate1);
			} else if (config.useSimpleReduce()) {
				trackingPyramidGraphs[i]
						.task("mapreduce" + i, IterativeClosestPoint::mapReduce, icpResultIntermediate1, pyramidTrackingResults[i])
						.transferToHost(DataTransferMode.EVERY_EXECUTION, icpResultIntermediate1);

			} else {
				trackingPyramidGraphs[i].transferToHost(DataTransferMode.EVERY_EXECUTION, pyramidTrackingResults[i]);
			}
        }

		integrateGraph = new TaskGraph("integrate")
				.transferToDevice(DataTransferMode.EVERY_EXECUTION, invTrack)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, scaledDepthImage, K, volumeDims, volume)
                .task("integrate", Integration::integrate, scaledDepthImage, invTrack, K, volumeDims, volume, mu, maxWeight);

        final ImageFloat3 verticies = referenceView.getVerticies();
        final ImageFloat3 normals = referenceView.getNormals();

		raycastGraph = new TaskGraph("raycast")
				.transferToDevice(DataTransferMode.EVERY_EXECUTION, referencePose)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, verticies, normals, volume, volumeDims)
                .task("raycast", Raycast::raycast, verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep, smallStep);

        renderTrackGraph = new TaskGraph("renderTrack")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, renderedTrackingImage, pyramidTrackingResults[0])
                .task("renderTrack", Renderer::renderTrack, renderedTrackingImage, pyramidTrackingResults[0]);


        renderGraph = new TaskGraph("render")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scenePose)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, renderedScene, volume, volumeDims, light, ambient, pyramidVerticies[0], pyramidNormals[0], verticies, normals, pyramidTrackingResults[0])
                .task("renderVolume", Renderer::renderVolume, renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep, largeStep, light, ambient);


        ImmutableTaskGraph itgProcessing = preprocessingGraph.snapshot();
        preprocessingPlan = new TornadoExecutionPlan(itgProcessing);

        preprocessingPlan.withDevice(tornadoDevice).withWarmUp();

        ImmutableTaskGraph itgEstimatePose = estimatePoseGraph.snapshot();
        estimatePosePlan = new TornadoExecutionPlan(itgEstimatePose);
        estimatePosePlan.withWarmUp().withDevice(tornadoDevice);

        int i = 0;
        trackingPyramidPlans = new TornadoExecutionPlan[trackingPyramidGraphs.length];
        for (TaskGraph trackingPyramid1 : trackingPyramidGraphs) {
            ImmutableTaskGraph itg = trackingPyramid1.snapshot();
            trackingPyramidPlans[i++] = new TornadoExecutionPlan(itg).withDevice(tornadoDevice).withWarmUp();
        }

        ImmutableTaskGraph itgIntegrate = integrateGraph.snapshot();
        integratePlan = new TornadoExecutionPlan(itgIntegrate).withDevice(tornadoDevice).withWarmUp();

        ImmutableTaskGraph itgRayCastGraph = raycastGraph.snapshot();
        raycastPlan = new TornadoExecutionPlan(itgRayCastGraph).withDevice(tornadoDevice).withWarmUp();


        ImmutableTaskGraph itgRenderTrack = renderTrackGraph.snapshot();
        renderTrackPlan = new TornadoExecutionPlan(itgRenderTrack).withDevice(tornadoDevice).withWarmUp();

        ImmutableTaskGraph itgRender = renderGraph.snapshot();
        renderPlan = new TornadoExecutionPlan(itgRender).withDevice(tornadoDevice).withWarmUp();
    }

    @Override
    protected void preprocessing() {
        if (EXECUTE_WITH_PROFILER) {
            preprocessingPlan.withDynamicReconfiguration(policy, DRMode.SERIAL).execute();
        } else {
            preprocessingPlan.execute();
        }
    }

    @Override
    protected void integrate() {
        invTrack.set(currentView.getPose());
        MatrixFloatOps.inverse(invTrack);

        if (EXECUTE_WITH_PROFILER) {
            integratePlan.withDynamicReconfiguration(policy, DRMode.SERIAL).execute();
        } else {
            integratePlan.execute();
        }
    }

    @Override
    protected boolean estimatePose() {

        invReferencePose.set(referenceView.getPose());
        MatrixFloatOps.inverse(invReferencePose);
        MatrixMath.sgemm(K, invReferencePose, projectReference);

        estimatePosePlan.execute();

        // perform ICP
        pyramidPose.set(currentView.getPose());
        for (int level = pyramidIterations.length - 1; level >= 0; level--) {
            for (int i = 0; i < pyramidIterations[level]; i++) {
                trackingPyramidPlans[level].execute();

                final boolean updated;
                if (config.useCustomReduce()) {
                    for (int k = 1; k < cus; k++) {
                        final int index = k * 32;
                        for (int j = 0; j < 32; j++) {
                            float value = icpResultIntermediate1.get(j) + icpResultIntermediate1.get(index + j);
                            icpResultIntermediate1.set(j, value);
                        }
                    }
                    trackingResult.resultImage = pyramidTrackingResults[level];
                    updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResultIntermediate1, pyramidPose, ICP_THRESHOLD);
                } else if (config.useSimpleReduce()) {
                    IterativeClosestPoint.reduceIntermediate(icpResult, icpResultIntermediate1);
                    trackingResult.resultImage = pyramidTrackingResults[level];
                    updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResult, pyramidPose, ICP_THRESHOLD);
                } else {
                    updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, pyramidTrackingResults[level], pyramidPose, ICP_THRESHOLD);
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
        return hasTracked;
    }

    @Override
    public void updateReferenceView() {
        referenceView.getPose().set(currentView.getPose());
        // convert the tracked pose into correct co-ordinate system for
        // raycasting which system (homogeneous co-ordinates? or virtual image?)
        MatrixMath.sgemm(currentView.getPose(), scaledInvK, referencePose);
        if (EXECUTE_WITH_PROFILER) {
            raycastPlan.withDynamicReconfiguration(policy, DRMode.SERIAL).execute();
        } else {
            raycastPlan.execute();
        }
    }

}
