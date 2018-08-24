/*
 *    This file is part of Slambench-Tornado: A Tornado version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-tornado
 *
 *    Copyright (c) 2013-2017 APT Group, School of Computer Science,
 *    The University of Manchester
 *
 *    This work is partially supported by EPSRC grants:
 *    Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *    Authors: James Clarkson
 */
package kfusion.pipeline;

import static uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath.getInverseCameraMatrix;
import static uk.ac.manchester.tornado.api.utils.TornadoUtilities.elapsedTimeInSeconds;
import static uk.ac.manchester.tornado.api.utils.TornadoUtilities.humanReadableByteCount;

import java.io.PrintStream;

import kfusion.TornadoModel;
import kfusion.devices.Device;
import kfusion.tornado.algorithms.Integration;
import kfusion.tornado.algorithms.IterativeClosestPoint;
import kfusion.tornado.algorithms.Raycast;
import uk.ac.manchester.tornado.api.TaskSchedule;
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
import uk.ac.manchester.tornado.api.mm.TaskMetaDataInterface;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.matrix.MatrixFloatOps;
import uk.ac.manchester.tornado.matrix.MatrixMath;

public class TornadoBenchmarkPipeline extends AbstractPipeline<TornadoModel> {

	private Float3 initialPosition;

	private TaskSchedule preprocessingSchedule;
	private TaskSchedule estimatePoseSchedule;
	private TaskSchedule trackingPyramid[];
	private TaskSchedule integrateSchedule;
	private TaskSchedule raycastSchedule;
	private TaskSchedule renderTrack;
	private TaskSchedule renderSchedule;

	private Matrix4x4Float[] scaledInvKs;
	private Matrix4x4Float pyramidPose;

	private float[] icpResultIntermediate1;
	private float[] icpResult;

	private int cus;

	private final PrintStream out;

	public static final float ICP_THRESHOLD = 1e-5f;

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

			out.println(
					"frame\tacquisition\tpreprocessing\ttracking\tintegration\traycasting\trendering\tcomputation\ttotal    \tX          \tY          \tZ         \ttracked   \tintegrated");

			final long[] timings = new long[7];

			timings[0] = System.nanoTime();
			boolean haveDepthImage = depthCamera.pollDepth(depthImageInput);
			videoCamera.skipVideoFrame();
			// @SuppressWarnings("unused")
			// boolean haveVideoImage = videoCamera.pollVideo(videoImageInput);

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
					renderTrack.execute();
					renderSchedule.execute();
				}

				timings[6] = System.nanoTime();
				final Float3 currentPos = currentView.getPose().column(3).asFloat3();
				final Float3 pos = Float3.sub(currentPos, initialPosition);

				out.printf("%d\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%d\t%d\n", frames,
						elapsedTimeInSeconds(timings[0], timings[1]), elapsedTimeInSeconds(timings[1], timings[2]),
						elapsedTimeInSeconds(timings[2], timings[3]), elapsedTimeInSeconds(timings[3], timings[4]),
						elapsedTimeInSeconds(timings[4], timings[5]), elapsedTimeInSeconds(timings[5], timings[6]),
						elapsedTimeInSeconds(timings[1], timings[5]), elapsedTimeInSeconds(timings[0], timings[6]),
						pos.getX(), pos.getY(), pos.getZ(), (hasTracked) ? 1 : 0, (doIntegrate) ? 1 : 0);
				frames++;
				timings[0] = System.nanoTime();
				haveDepthImage = depthCamera.pollDepth(depthImageInput);
				videoCamera.skipVideoFrame();
			}

			if (config.printKernels()) {
				preprocessingSchedule.dumpProfiles();
				estimatePoseSchedule.dumpProfiles();
				for (TaskSchedule trackingPyramid1 : trackingPyramid) {
					trackingPyramid1.dumpProfiles();
				}
				integrateSchedule.dumpProfiles();
				raycastSchedule.dumpProfiles();
				renderTrack.dumpProfiles();
				renderSchedule.dumpProfiles();
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
		final TornadoDevice tornadoDevice = (TornadoDevice) config.getTornadoDevice();
		info("mapping onto %s\n", tornadoDevice.toString());

		final long localMemSize = tornadoDevice.getDevice().getLocalMemorySize();
		final float fraction = Float.parseFloat(TornadoRuntime.getProperty("kfusion.reduce.fraction", "1.0"));
		cus = (int) (tornadoDevice.getDevice().getMaxComputeUnits() * fraction);
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
        preprocessingSchedule = new TaskSchedule("pp")
                .streamIn(depthImageInput)
                .task("mm2meters", ImagingOps::mm2metersKernel, scaledDepthImage, depthImageInput, scalingFactor)
                .task("bilateralFilter", ImagingOps::bilateralFilter, pyramidDepths[0], scaledDepthImage, gaussian, eDelta, radius)
                .mapAllTo(tornadoDevice);
        //@formatter:on

		final int iterations = pyramidIterations.length;
		scaledInvKs = new Matrix4x4Float[iterations];
		for (int i = 0; i < iterations; i++) {
			final Float4 cameraDup = Float4.mult(scaledCamera, 1f / (1 << i));
			scaledInvKs[i] = new Matrix4x4Float();
			getInverseCameraMatrix(cameraDup, scaledInvKs[i]);
		}

		estimatePoseSchedule = new TaskSchedule("estimatePose");

		for (int i = 1; i < iterations; i++) {
			//@formatter:off
            estimatePoseSchedule
                    .task("resizeImage" + i, ImagingOps::resizeImage6, pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2);
            //@formatter:on
		}

		for (int i = 0; i < iterations; i++) {
			//@formatter:off
            estimatePoseSchedule
                    .task("d2v" + i, GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i])
                    .task("v2n" + i, GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]);
            //@formatter:on
		}

		estimatePoseSchedule.streamIn(projectReference).mapAllTo(tornadoDevice);

		if (config.useCustomReduce()) {
			icpResultIntermediate1 = new float[cus * 32];
		} else if (config.useSimpleReduce()) {
			icpResultIntermediate1 = new float[config.getReductionSize() * 32];
		}

		trackingPyramid = new TaskSchedule[iterations];

		for (int i = 0; i < iterations; i++) {
			//@formatter:off
            trackingPyramid[i] = new TaskSchedule("icp" + i)
                    .streamIn(pyramidPose)
                    .task("track" + i, IterativeClosestPoint::trackPose,
                            pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                            referenceView.getVerticies(), referenceView.getNormals(), pyramidPose,
                            projectReference, distanceThreshold, normalThreshold);

            if (config.useCustomReduce()) {
                final ImageFloat8 result = pyramidTrackingResults[i];
                final int numElements = result.X() * result.Y();
                final int numWgs = Math.min(roundToWgs(numElements / cus, 128), maxwgs);
                
                trackingPyramid[i]
                        .prebuiltTask("customReduce" + i,
                                tornadoDevice.getDeviceContext().needsBump() ? "optMapReduceBump" : "optMapReduce",
                                "./opencl/optMapReduce.cl",
                                new Object[]{icpResultIntermediate1, result, result.X(), result.Y()},
                                new Access[]{Access.WRITE, Access.READ, Access.READ, Access.READ},
                                tornadoDevice,
                                new int[]{numWgs})
                        .streamOut(icpResultIntermediate1);

                TaskMetaDataInterface meta = trackingPyramid[i].getTask("icp" + i + "." + "customReduce" + i).meta();
                String compilerFlags = meta.getCompilerFlags();
                meta.setCompilerFlags(compilerFlags + " -DWGS=" + maxBinsPerCU);
                meta.setGlobalWork(new long[]{maxwgs});
                meta.setLocalWork(new long[]{maxBinsPerCU});
            } else if (config.useSimpleReduce()) {
            	
                trackingPyramid[i]
                       .task("mapreduce" + i, IterativeClosestPoint::mapReduce, icpResultIntermediate1, pyramidTrackingResults[i])
                       .streamOut(icpResultIntermediate1);
            	
//                trackingPyramid[i]
//                        .task("mapInitData" + i, IterativeClosestPoint::mapInitData, icpResultIntermediate1, pyramidTrackingResults[i])
//                        .task("reduceData" + i, IterativeClosestPoint::reduceData, icpResultIntermediate1, pyramidTrackingResults[i])
//                        .streamOut(icpResultIntermediate1);
                
                // XXX: perform final reduction from partial reduction after copy out on CPU.
                
            } else {
                trackingPyramid[i].streamOut(pyramidTrackingResults[i]);
            }
            //@formatter:on

			trackingPyramid[i].mapAllTo(tornadoDevice);
		}

		//@formatter:off
        integrateSchedule = new TaskSchedule("integrate")
                .streamIn(invTrack)
                .task("integrate", Integration::integrate, scaledDepthImage, invTrack, K, volumeDims, volume, mu, maxWeight)
                .mapAllTo(tornadoDevice);
        //@formatter:on

		final ImageFloat3 verticies = referenceView.getVerticies();
		final ImageFloat3 normals = referenceView.getNormals();

		//@formatter:off
        raycastSchedule = new TaskSchedule("raycast")
                .streamIn(referencePose)
                .task("raycast", Raycast::raycast, verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep, smallStep)
                .mapAllTo(tornadoDevice);
        //@formatter:on

		//@formatter:off
//        renderSchedule = new TaskSchedule("render")
//                .streamIn(scenePose)
//                .task("renderTrack", Renderer::renderTrack, renderedTrackingImage, pyramidTrackingResults[0])
//                //BUG need to investigate crashes in render depth
//                //                .task("renderDepth", Renderer::renderDepth, renderedDepthImage, filteredDepthImage, nearPlane, farPlane)
//                .task("renderVolume", Renderer::renderVolume,
//                        renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep,
//                        largeStep, light, ambient)
//                .mapAllTo(oclDevice);
        //@formatter:on

		renderTrack = new TaskSchedule("renderTrack")
				.task("renderTrack", Renderer::renderTrack, renderedTrackingImage, pyramidTrackingResults[0])
				.mapAllTo(tornadoDevice);

		renderSchedule = new TaskSchedule("render").streamIn(scenePose)
				.task("renderVolume", Renderer::renderVolume, renderedScene, volume, volumeDims, scenePose, nearPlane,
						farPlane * 2f, smallStep, largeStep, light, ambient)
				.mapAllTo(tornadoDevice);

		preprocessingSchedule.warmup();
		estimatePoseSchedule.warmup();
		for (TaskSchedule trackingPyramid1 : trackingPyramid) {
			trackingPyramid1.warmup();
		}
		integrateSchedule.warmup();
		raycastSchedule.warmup();
		renderTrack.warmup();
		renderSchedule.warmup();
	}

	@Override
	protected void preprocessing() {
		preprocessingSchedule.execute();
	}

	@Override
	protected void integrate() {
		invTrack.set(currentView.getPose());
		MatrixFloatOps.inverse(invTrack);

		integrateSchedule.execute();
	}

	@Override
	protected boolean estimatePose() {

		invReferencePose.set(referenceView.getPose());
		MatrixFloatOps.inverse(invReferencePose);
		MatrixMath.sgemm(K, invReferencePose, projectReference);

		estimatePoseSchedule.execute();

		// perform ICP
		pyramidPose.set(currentView.getPose());
		for (int level = pyramidIterations.length - 1; level >= 0; level--) {
			for (int i = 0; i < pyramidIterations[level]; i++) {
				trackingPyramid[level].execute();

				final boolean updated;
				if (config.useCustomReduce()) {
					for (int k = 1; k < cus; k++) {
						final int index = k * 32;
						for (int j = 0; j < 32; j++) {
							icpResultIntermediate1[j] += icpResultIntermediate1[index + j];
						}
					}
					trackingResult.resultImage = pyramidTrackingResults[level];
					updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResultIntermediate1,
							pyramidPose, ICP_THRESHOLD);
				} else if (config.useSimpleReduce()) {
					IterativeClosestPoint.reduceIntermediate(icpResult, icpResultIntermediate1);
					trackingResult.resultImage = pyramidTrackingResults[level];
					updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResult, pyramidPose,
							ICP_THRESHOLD);
				} else {
					updated = IterativeClosestPoint.estimateNewPose(config, trackingResult,
							pyramidTrackingResults[level], pyramidPose, ICP_THRESHOLD);
				}

				pyramidPose.set(trackingResult.getPose());

				if (updated) {
					break;
				}
			}
		}

		// if the tracking result meets our constraints, update the current view
		// with
		// the estimated
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
		raycastSchedule.execute();
	}
}
