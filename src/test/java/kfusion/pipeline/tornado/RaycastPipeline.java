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
package kfusion.pipeline.tornado;

import kfusion.TornadoModel;
import kfusion.Utils;
import kfusion.devices.Device;
import kfusion.devices.TestingDevice;
import kfusion.pipeline.AbstractPipeline;
import kfusion.tornado.algorithms.Raycast;
import tornado.collections.graphics.GraphicsMath;
import tornado.collections.matrix.MatrixMath;
import tornado.collections.types.FloatingPointError;
import tornado.collections.types.ImageFloat3;
import tornado.runtime.api.TaskSchedule;

public class RaycastPipeline extends AbstractPipeline<TornadoModel> {

    public RaycastPipeline(TornadoModel config) {
        super(config);
    }

    private ImageFloat3 refVerticies;
    private ImageFloat3 refNormals;

    private TaskSchedule graph;

    @Override
    public void configure(Device device) {
        super.configure(device);

        final ImageFloat3 verticies = referenceView.getVerticies();
        final ImageFloat3 normals = referenceView.getNormals();

//		final DomainTree domain = new DomainTree(2);
//		domain.set(0, new IntDomain(verticies.X()));
//		domain.set(1, new IntDomain(verticies.Y()));
//        System.out.printf("farPlane: %f\n", farPlane);
//		final TornadoExecuteTask raycast = Raycast.raycastCode.invoke(
//			verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep,
//			smallStep);
//
//		raycast.disableJIT();
//		raycast.meta().addProvider(
//				DomainTree.class, domain);
//		raycast.mapTo(EXTERNAL_GPU);
//		raycast.loadFromFile("opencl/raycast.cl");
//        final PrebuiltTask raycast = TaskUtils.createTask("customRaycast",
//                "raycast",
//                "./opencl/raycast.cl",
//                new Object[]{verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep,
//                    smallStep},
//                new Access[]{Access.READ_WRITE, Access.READ_WRITE, Access.READ, Access.READ, Access.READ, Access.READ, Access.READ, Access.READ, Access.READ},
//                config.getTornadoDevice(),
//                new int[]{verticies.X(), verticies.Y()});
        //@formatter:off
        graph = new TaskSchedule("s0")
                .streamIn(referencePose, volume, volumeDims)
                .task("raycast", Raycast::raycast,
                        verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep,
                        smallStep)
                //                .task(raycast)
                .streamOut(verticies, normals)
                .mapAllTo(config.getTornadoDevice());
        //@formatter:on

    }

    private static String makeFilename(String path, int frame, String kernel, String variable,
            boolean isInput) {
        return String.format(
                "%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in" : "out");
    }

    private void loadFrame(String path, int index) {
        try {
            refVerticies = referenceView.getVerticies().duplicate();
            refNormals = referenceView.getNormals().duplicate();

            Utils.loadData(
                    makeFilename(
                            path, index, "raycasting", "volume", true), volume.asBuffer());
            Utils.loadData(
                    makeFilename(
                            path, index, "raycasting", "volumeDims", true), volumeDims.asBuffer());
            Utils.loadData(
                    makeFilename(
                            path, index, "raycasting", "k", true), scaledCamera.asBuffer());
            GraphicsMath.getCameraMatrix(
                    scaledCamera, K);
            GraphicsMath.getInverseCameraMatrix(
                    scaledCamera, scaledInvK);
            Utils.loadData(
                    makeFilename(
                            path, index, "raycasting", "pose", true), currentView.getPose().asBuffer());

            Utils.loadData(
                    makeFilename(
                            path, index, "raycasting", "vertex", false), refVerticies.asBuffer());
            Utils.loadData(
                    makeFilename(
                            path, index, "raycasting", "normal", false), refNormals.asBuffer());

            // System.out.printf("ref inv pose:\n%s\n",refInversePose.toString(FloatOps.fmt4em));
            // System.out.printf("ref K       :\n%s\n",refK.toString(FloatOps.fmt4em));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean validate(String path, int index) {

        FloatingPointError vertexError = referenceView.getVerticies().calculateULP(
                refVerticies);
        System.out.printf(
                "\tvertex errors: %s\n", vertexError.toString());

        FloatingPointError normalError = referenceView.getNormals().calculateULP(
                refNormals);
        System.out.printf(
                "\tnormal errors: %sf\n", normalError);

        return (vertexError.getMaxUlp() > 5f || normalError.getMaxUlp() > 5f) ? false : true;

    }

//	@Override
//	public void updateReferenceView() {
//		if (KfusionModel.DEBUG) {
//			info("============== updating reference view ==============");
//		}
//
//		referenceView.getPose().set(
//			currentView.getPose());
//
//		// convert the tracked pose into correct co-ordinate system for
//		// raycasting
//		// which system (homogeneous co-ordinates? or virtual image?)
//		MatrixMath.sgemm(
//			currentView.getPose(), scaledInvK, referencePose);
//		if (KfusionModel.DEBUG) {
//			info(
//				"current   pose: %s", currentView.getPose().toString());
//			info(
//				"reference pose: %s", referencePose.toString());
//		}
//
//		graph.schedule().waitOn();
//	}
    @Override
    public void execute() {

//		updateReferenceView();
//	    if (config.debug()) {
//            info("============== updating reference view ==============");
//        }
//
        final ImageFloat3 verticies = referenceView.getVerticies().duplicate();
        final ImageFloat3 normals = referenceView.getNormals().duplicate();

        referenceView.getPose().set(currentView.getPose());

        // convert the tracked pose into correct co-ordinate system for
        // raycasting
        // which system (homogeneous co-ordinates? or virtual image?)
        MatrixMath.sgemm(currentView.getPose(), scaledInvK, referencePose);
        if (config.debug()) {
            info("current   pose: %s", currentView.getPose().toString());
            info("reference pose: %s", referencePose.toString());
        }

//        Raycast.raycast(verticies, normals, volume, volumeDims, referencePose,
//                nearPlane, farPlane, largeStep, smallStep);
        graph.execute();

        System.out.printf("verticies [J]: %s\n", verticies.summerise());
        System.out.printf("verticies [T]: %s\n", referenceView.getVerticies().summerise());
        System.out.printf("verticies [R]: %s\n", refVerticies.summerise());

//	    FloatingPointError vertexError = verticies.calculateULP(
//	            refVerticies);
//	        System.out.printf(
//	            "\tvertex errors: %s\n", vertexError.toString());
//
//	       vertexError =  referenceView.getVerticies().calculateULP(
//	                refVerticies);
//	            System.out.printf(
//	                "\tvertex errors: %s\n", vertexError.toString());
        System.out.printf("normals   [J]: %s\n", normals.summerise());
        System.out.printf("normals   [T]: %s\n", referenceView.getNormals().summerise());
        System.out.printf("normals   [R]: %s\n", refNormals.summerise());

//        vertexError =  normals.calculateULP(
//                refNormals);
//            System.out.printf(
//                "\tnormals errors: %s\n", vertexError.toString());
//
//            vertexError =  referenceView.getNormals().calculateULP(
//                    refNormals);
//                System.out.printf(
//                    "\tnormals errors: %s\n", vertexError.toString());
        graph.dumpTimes();

    }

    public static void main(String[] args) {
        final String path = args[0];
        final int numFrames = Integer.parseInt(args[1]);
        TornadoModel config = new TornadoModel();

        config.setDevice(new TestingDevice());

        RaycastPipeline kernel = new RaycastPipeline(config);
        kernel.reset();

        int validFrames = 0;
        for (int i = 0; i < numFrames; i++) {
//			 int i = 15;
            System.out.printf(
                    "frame %d:\n", i);
            kernel.loadFrame(
                    path, i);
            kernel.execute();
            boolean valid = kernel.validate(
                    path, i);
            System.out.printf(
                    "\tframe %s valid\n", (valid) ? "is" : "is not");
            if (valid) {
                validFrames++;
            }
        }

        double pctValid = (((double) validFrames) / ((double) numFrames)) * 100.0;
        System.out.printf(
                "Found %d valid frames (%.2f %%)\n", validFrames, pctValid);

    }

}
