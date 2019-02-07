/*
 *    This file is part of Slambench-Tornado: A Tornado version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-tornado
 *
 *    Copyright (c) 2013-2019 APT Group, School of Computer Science,
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

import kfusion.java.common.Utils;
import kfusion.java.devices.Device;
import kfusion.java.devices.TestingDevice;
import kfusion.java.pipeline.AbstractPipeline;
import kfusion.tornado.algorithms.Raycast;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.types.FloatingPointError;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.matrix.MatrixMath;

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

    private static String makeFilename(String path, int frame, String kernel, String variable, boolean isInput) {
        return String.format("%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in" : "out");
    }

    private void loadFrame(String path, int index) {
        try {
            refVerticies = referenceView.getVerticies().duplicate();
            refNormals = referenceView.getNormals().duplicate();

            Utils.loadData(makeFilename(path, index, "raycasting", "volume", true), volume.asBuffer());
            Utils.loadData(makeFilename(path, index, "raycasting", "volumeDims", true), volumeDims.asBuffer());
            Utils.loadData(makeFilename(path, index, "raycasting", "k", true), scaledCamera.asBuffer());
            GraphicsMath.getCameraMatrix(scaledCamera, K);
            GraphicsMath.getInverseCameraMatrix(scaledCamera, scaledInvK);
            Utils.loadData(makeFilename(path, index, "raycasting", "pose", true), currentView.getPose().asBuffer());

            Utils.loadData(makeFilename(path, index, "raycasting", "vertex", false), refVerticies.asBuffer());
            Utils.loadData(makeFilename(path, index, "raycasting", "normal", false), refNormals.asBuffer());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean validate(String path, int index) {

        FloatingPointError vertexError = referenceView.getVerticies().calculateULP(refVerticies);
        System.out.printf("\tvertex errors: %s\n", vertexError.toString());

        FloatingPointError normalError = referenceView.getNormals().calculateULP(refNormals);
        System.out.printf("\tnormal errors: %sf\n", normalError);

        return (vertexError.getMaxUlp() > 5f || normalError.getMaxUlp() > 5f) ? false : true;

    }

    @Override
    public void execute() {
    	
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

        Raycast.raycast(verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep, smallStep);

        graph.execute();

        System.out.printf("verticies [J]: %s\n", verticies.summerise());
        System.out.printf("verticies [T]: %s\n", referenceView.getVerticies().summerise());
        System.out.printf("verticies [R]: %s\n", refVerticies.summerise());

        System.out.printf("normals   [J]: %s\n", normals.summerise());
        System.out.printf("normals   [T]: %s\n", referenceView.getNormals().summerise());
        System.out.printf("normals   [R]: %s\n", refNormals.summerise());

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
            // int i = 15;
            System.out.printf("frame %d:\n", i);
            kernel.loadFrame(path, i);
            kernel.execute();
            boolean valid = kernel.validate(path, i);
            System.out.printf("\tframe %s valid\n", (valid) ? "is" : "is not");
            if (valid) {
                validFrames++;
            }
        }

        double pctValid = (((double) validFrames) / ((double) numFrames)) * 100.0;
        System.out.printf("Found %d valid frames (%.2f %%)\n", validFrames, pctValid);

    }

}
