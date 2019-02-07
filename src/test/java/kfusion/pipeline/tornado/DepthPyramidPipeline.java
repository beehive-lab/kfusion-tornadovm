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

import kfusion.Utils;
import kfusion.devices.Device;
import kfusion.devices.TestingDevice;
import kfusion.pipeline.AbstractPipeline;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.types.FloatingPointError;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;

public class DepthPyramidPipeline extends AbstractPipeline<TornadoModel> {

    public DepthPyramidPipeline(TornadoModel config) {
        super(config);
    }

    private Matrix4x4Float[] invK;
    private ImageFloat3[] refVerticies;
    private ImageFloat3[] refNormals;

    private TaskSchedule vertexGraph;

    private static String makeFilename(String path, int frame, String kernel, String variable, boolean isInput) {
        return String.format("%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in" : "out");
    }

    @Override
    public void configure(Device device) {
        super.configure(device);

        invK = new Matrix4x4Float[3];
        refVerticies = new ImageFloat3[3];
        refNormals = new ImageFloat3[3];

        vertexGraph = new TaskSchedule("s0");

        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();

        for (int i = 0; i < pyramidIterations.length; i++) {
            // int i = 0;
            invK[i] = new Matrix4x4Float();
            refVerticies[i] = pyramidVerticies[i].duplicate();
            refNormals[i] = pyramidNormals[i].duplicate();

            vertexGraph.streamIn(pyramidDepths[i], invK[i]).task("d2v" + i, GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], invK[i])
                    .task("v2n" + i, GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]).streamOut(pyramidVerticies[i], pyramidNormals[i]);
        }

        vertexGraph.mapAllTo(config.getTornadoDevice());
    }

    private void loadFrame(String path, int index) {

        try {

            for (int i = 0; i < 3; i++) {
                Utils.loadData(makeFilename(path, index, "depth2vertex_" + i, "depths", true), pyramidDepths[i].asBuffer());
                Utils.loadData(makeFilename(path, index, "depth2vertex_" + i, "invK", true), invK[i].asBuffer());

                Utils.loadData(makeFilename(path, index, "depth2vertex_" + i, "verticies", false), refVerticies[i].asBuffer());
                Utils.loadData(makeFilename(path, index, "vertex2normal_" + i, "normals", false), refNormals[i].asBuffer());

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean validate(String path, int index) {
        System.out.println("Validation:");
        boolean valid = true;
        for (int i = 0; i < pyramidIterations.length; i++) {
            // int i = 0;
            final FloatingPointError verticiesError = pyramidVerticies[i].calculateULP(refVerticies[i]);
            System.out.printf("\tlevel %d: vertex  - %s\n", i, pyramidVerticies[i].summerise());
            System.out.printf("\tlevel %d: vertex  error - %s\n", i, verticiesError.toString());

            final FloatingPointError normalsError = pyramidNormals[i].calculateULP(refNormals[i]);
            System.out.printf("\tlevel %d: normals - %s\n", i, pyramidNormals[i].summerise());
            System.out.printf("\tlevel %d: normals error - %s\n", i, normalsError.toString());

            valid &= (verticiesError.getMaxUlp() < 5f && normalsError.getMaxUlp() < 5f);
        }
        return valid;
    }

    @Override
    public void execute() {
        vertexGraph.execute();
        vertexGraph.dumpTimes();
    }

    public static void main(String[] args) {
        final String path = args[0];
        final int numFrames = Integer.parseInt(args[1]);

        TornadoModel config = new TornadoModel();

        config.setDevice(new TestingDevice());

        final DepthPyramidPipeline kernel = new DepthPyramidPipeline(config);
        kernel.reset();

        int validFrames = 0;
        for (int i = 0; i < numFrames; i++) {
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
