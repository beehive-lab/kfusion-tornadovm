package kfusion.pipeline;

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
import tornado.collections.types.Float3;
import tornado.collections.types.Float4;
import static tornado.collections.types.Float4.mult;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.common.RuntimeUtilities;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.TaskGraph;
import static tornado.runtime.api.TaskUtils.createTask;
public class TornadoBenchmarkPipeline extends AbstractPipeline<TornadoModel> {

	private final Float3				initialPosition;
	
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

	public TornadoBenchmarkPipeline(TornadoModel config) {
		super(config);
		initialPosition = new Float3();
	}

	@Override
	public void execute() {
		if (config.getDevice() != null) {
			System.out
					.println("frame\tacquisition\tpreprocessing\ttracking\tintegration\traycasting\trendering\tcomputation\ttotal    \tX          \tY          \tZ         \ttracked   \tintegrated");
			
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
				if (doIntegrate)
					integrate();
				
					timings[4] = System.nanoTime();

				final boolean doUpdate = frames > 2;
					
					if(doUpdate)
						updateReferenceView();
					
					timings[5] = System.nanoTime();
					
				
				/*
				 * missing rendering code here
				 */
				if(frames % renderingRate == 0)
					renderGraph.schedule().waitOn();
					
				timings[6] = System.nanoTime();
				final Float3 currentPos = currentView.getPose().column(3).asFloat3();
				//System.out.printf("[%d]: pos=%s\n",frames,currentPos.toString());
				final Float3 pos = Float3.sub(currentPos, initialPosition);

				System.out
						.printf("%d\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%d\t%d\n", frames,
								RuntimeUtilities.elapsedTimeInSeconds(timings[0],timings[1]),
								RuntimeUtilities.elapsedTimeInSeconds(timings[1],timings[2]),
								RuntimeUtilities.elapsedTimeInSeconds(timings[2],timings[3]),
								RuntimeUtilities.elapsedTimeInSeconds(timings[3],timings[4]),
								RuntimeUtilities.elapsedTimeInSeconds(timings[4],timings[5]), 
								RuntimeUtilities.elapsedTimeInSeconds(timings[5],timings[6]), 
								RuntimeUtilities.elapsedTimeInSeconds(timings[1],timings[5]), 
								RuntimeUtilities.elapsedTimeInSeconds(timings[0],timings[6]), 
								//RuntimeUtilities.elapsedTimeInSeconds(timings[0],timings[1]),
								pos.getX(), pos.getY(), pos.getZ(), 
								(hasTracked) ? 1 : 0, (doIntegrate) ? 1 : 0);

				frames++;
				
				timings[0] = System.nanoTime();
				haveDepthImage = depthCamera.pollDepth(depthImageInput);
				videoCamera.skipVideoFrame();
//				haveVideoImage = videoCamera.pollVideo(videoImageInput);
			}
			
			if(config.printKernels()){
			    preprocessingGraph.dumpProfiles();
			    estimatePoseGraph.dumpProfiles();
			    for(TaskGraph graph : trackingPyramid){
			        graph.dumpProfiles();
			    }
			    integrateGraph.dumpProfiles();
			    raycastGraph.dumpProfiles();
			    renderGraph.dumpProfiles();
			}
			
		}
	}

	public void configure(Device device) {
		super.configure(device);

		Float3.mult(config.getOffset(), volumeDims, initialPosition);
		
//		System.err.println("init pos: " + initialPosition.toString());

		frames = 0;
		
		/**
		 * Tornado tasks
		 */
		
		final OCLDeviceMapping deviceMapping = (OCLDeviceMapping) config.getTornadoDevice();
		System.err.printf("mapping onto %s\n",deviceMapping.toString());
		
		pyramidPose = new Matrix4x4Float();
		pyramidDepths[0] = filteredDepthImage;
		pyramidVerticies[0] = currentView.getVerticies();
		pyramidNormals[0] = currentView.getNormals();
                icpResultIntermediate1 = new float[2048*32];
                icpResultIntermediate2 = new float[512*32];
                icpResult = new float[32];
		
		final Matrix4x4Float scenePose = sceneView.getPose();
		
		//@formatter:off
		preprocessingGraph = new TaskGraph()
			.streamIn(depthImageInput)
			.add(ImagingOps::mm2metersKernel,scaledDepthImage, depthImageInput, scalingFactor)
			.add(ImagingOps::bilateralFilter,pyramidDepths[0], scaledDepthImage, gaussian, eDelta, radius)
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
				.add(ImagingOps::resizeImage6,pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2);
			//@formatter:on
		}

		for (int i = 0; i < iterations; i++) {
			//@formatter:off
			estimatePoseGraph
				.add(GraphicsMath::depth2vertex,pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i])
				.add(GraphicsMath::vertex2normal,pyramidNormals[i], pyramidVerticies[i]);
			//@formatter:on
		}
		
		estimatePoseGraph
			.streamIn(projectReference)
			.mapAllTo(deviceMapping);
		
		trackingPyramid = new TaskGraph[iterations];
		for (int i = 0; i < iterations; i++) {

			final CompilableTask trackPose = createTask(IterativeClosestPoint::trackPose,
				pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
				referenceView.getVerticies(), referenceView.getNormals(), pyramidPose,
				projectReference, distanceThreshold, normalThreshold);
		    

			//@formatter:off
			trackingPyramid[i] = new TaskGraph()
					.streamIn(pyramidPose)
					.add(trackPose)
                                .add(IterativeClosestPoint::mapReduce,icpResultIntermediate1,pyramidTrackingResults[i])
//                                .add(IterativeClosestPoint::reduceIntermediate,icpResultIntermediate2,icpResultIntermediate1)
//                                .add(IterativeClosestPoint::reduceIntermediate,icpResult,icpResultIntermediate2)
					.streamOut(icpResultIntermediate1)
					.mapAllTo(deviceMapping);
			//@formatter:on
		}
		
		//@formatter:off
		integrateGraph = new TaskGraph()
			.streamIn(invTrack)
			.add(Integration::integrate,scaledDepthImage, invTrack, K, volumeDims, volume, mu, maxWeight)
			.mapAllTo(deviceMapping);
		//@formatter:on
		
		final ImageFloat3 verticies = referenceView.getVerticies();
		final ImageFloat3 normals = referenceView.getNormals();
		
		
		
		CompilableTask raycast = createTask(Raycast::raycast,verticies,normals,volume,volumeDims,referencePose,nearPlane,farPlane,largeStep,smallStep);
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
			.add(Renderer::renderTrack,renderedTrackingImage, pyramidTrackingResults[0])
//			.add(Renderer::renderDepth,renderedDepthImage, filteredDepthImage, nearPlane, farPlane)	
			.add(renderVolume)
			.mapAllTo(deviceMapping);
		//@formatter:on
		
	}
	
	protected void preprocessing(){
		preprocessingGraph.schedule().waitOn();
	}
	
	@Override
	protected void integrate() {
		invTrack.set(currentView.getPose());
		MatrixFloatOps.inverse(invTrack);

		integrateGraph.schedule().waitOn();
	}

	protected boolean estimatePose(){
		
		
		invReferencePose.set(referenceView.getPose());
		MatrixFloatOps.inverse(invReferencePose);
		MatrixMath.sgemm(K, invReferencePose, projectReference);
		
		estimatePoseGraph.schedule().waitOn();
		
		// perform ICP
		pyramidPose.set(currentView.getPose());
		for (int level = pyramidIterations.length - 1; level >= 0; level--) {
			for (int i = 0; i < pyramidIterations[level]; i++) {
				trackingPyramid[level].schedule().waitOn();
                                
                                IterativeClosestPoint.reduceIntermediate(icpResult, icpResultIntermediate1);

                                
                                
                                 trackingResult.resultImage = pyramidTrackingResults[level];
                final boolean updated = IterativeClosestPoint.estimateNewPose(config,trackingResult,icpResult,pyramidPose,1e-5f);
                
                                
//				boolean updated = IterativeClosestPoint.estimateNewPose(config, trackingResult,
//						pyramidTrackingResults[level], pyramidPose, 1e-5f);

				//System.out.printf("tracking: %s\n",trackingResult.toString());
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
