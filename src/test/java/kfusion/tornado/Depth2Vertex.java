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
package kfusion.tornado;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import kfusion.java.common.Utils;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.utils.TornadoUtilities;

public class Depth2Vertex {

    final private TornadoModel config = new TornadoModel();
    final private String FILE_PATH = "/Users/jamesclarkson/Downloads/kfusion_ut_data";
    final private float EPSILON = 1e-7f;

    final private int[] frames = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
    final private int[] widths = { 320, 160, 80, 320, 160, 80, 320, 160, 80 };
    final private int[] heights = { 240, 120, 60, 240, 120, 60, 240, 120, 60 };

    private ImageFloat3[] vertex;
    private ImageFloat3[] vertexTruth;

    private ImageFloat3[] normal;
    private ImageFloat3[] normalTruth;

    private ImageFloat[] depth;

    private Matrix4x4Float invK;

    final String d2v_prefix = "depth2vertex_";
    final String v2n_prefix = "vertex2normal_";

    TaskGraph depthToVertex;
    TaskGraph vertexToNormal;

    @Before
    public void setUp() throws Exception {

        vertex = new ImageFloat3[3];
        vertexTruth = new ImageFloat3[3];

        normal = new ImageFloat3[3];
        normalTruth = new ImageFloat3[3];

        depth = new ImageFloat[3];

        for (int i = 0; i < 3; i++) {
            vertex[i] = new ImageFloat3(widths[i], heights[i]);
            vertexTruth[i] = new ImageFloat3(widths[i], heights[i]);

            normal[i] = new ImageFloat3(widths[i], heights[i]);
            normalTruth[i] = new ImageFloat3(widths[i], heights[i]);

            depth[i] = new ImageFloat(widths[i], heights[i]);
        }

        invK = new Matrix4x4Float();

        //@formatter:off
        depthToVertex = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, depth[0], invK)
                .task("d2v", GraphicsMath::depth2vertex, vertex[0], depth[0], invK)
                .transferToHost(vertex[0])
                .mapAllTo(config.getTornadoDevice());
        //@formatter:on

        //@formatter:off
        vertexToNormal = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, vertex[0])
                .task("v2n", GraphicsMath::vertex2normal, normal[0], vertex[0])
                .transferToHost(normal[0])
                .mapAllTo(config.getTornadoDevice());
        //@formatter:on
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testDepth2vertex() {

        int errors = 0;
        int refIndex = 0;
        for (int index = 0; index < frames.length; index += 3) {
            System.out.println("frame: " + index);

            int frame = frames[index];
            int width = widths[index];
            int height = heights[index];

            try {
                Utils.loadData(String.format("%s/%svertex.out.%04d", FILE_PATH, d2v_prefix, frame), vertexTruth[refIndex].asBuffer());

                Utils.loadData(String.format("%s/%sinvk.in.%04d", FILE_PATH, d2v_prefix, frame), invK.asBuffer());

                Utils.loadData(String.format("%s/%sdepth.in.%04d", FILE_PATH, d2v_prefix, frame), depth[refIndex].asBuffer());
            } catch (Exception e) {
                System.out.printf("depth2vertex: [%d] %s\n", frame, e.getMessage());
                e.printStackTrace();
            }

            if (config.useTornado()) {
                depthToVertex.schedule().waitOn();
                depthToVertex.dumpTimes();
                // System.out.printf("depth2vertex: execution time=%f total
                // time=%f\n",task.getExecutionTime(),
                // task.getTotalTime());

            } else {
                long start = System.nanoTime();
                GraphicsMath.depth2vertex(vertex[refIndex], depth[refIndex], invK);
                long end = System.nanoTime();
                System.out.printf("depth2vertex: executime time=%f\n", TornadoUtilities.elapsedTimeInSeconds(start, end));
            }

            float maxULP = calculateULP(vertex[refIndex], vertexTruth[refIndex]);
            System.out.printf("depth2vertex: [%d] max ulp=%f\n", frame, maxULP);

            if (maxULP > config.getMaxULP()) {
                errors++;
            }

            refIndex = (refIndex + 3) % 3;
        }

        if (errors > 0) {
            System.out.println(String.format("Found %d errors", errors));
        }
    }

    private static float calculateULP(ImageFloat3 value, ImageFloat3 ref) {
        float maxULP = Float.MIN_VALUE;
        float minULP = Float.MAX_VALUE;
        float averageULP = 0f;

        for (int j = 0; j < value.Y(); j++) {
            for (int i = 0; i < value.X(); i++) {
                final Float3 v = value.get(i, j);
                final Float3 r = ref.get(i, j);

                if (v.getX() != -2f && r.getX() != -2f) {
                    final float ulpFactor = Float3.findULPDistance(v, r);

                    averageULP += ulpFactor;
                    minULP = Math.min(ulpFactor, minULP);
                    maxULP = Math.max(ulpFactor, maxULP);

                    // if (ulpFactor > 5f) {
                    // System.out.printf("error: %s != %s\n", v.toString(FloatOps.fmt3e),
                    // r.toString(FloatOps.fmt3e));
                    // }
                }
            }
        }

        averageULP /= (float) value.X() * value.Y();
        System.out.printf("image: mean ulp=%f, min=%f, max=%f\n", averageULP, minULP, maxULP);

        return maxULP;
    }

    @Test
    public void testVertex2Normal() {
        int refIndex = 0;
        int errors = 0;
        for (int index = 0; index < frames.length; index += 3) {
            int frame = frames[index];
            int width = widths[index];
            int height = heights[index];

            try {
                Utils.loadData(String.format("%s/%svertex.out.%04d", FILE_PATH, d2v_prefix, frame), vertex[refIndex].asBuffer());

                Utils.loadData(String.format("%s/%snormal.out.%04d", FILE_PATH, v2n_prefix, frame), normalTruth[refIndex].asBuffer());

                Utils.loadData(String.format("%s/%sdepth.in.%04d", FILE_PATH, d2v_prefix, frame), depth[refIndex].asBuffer());
            } catch (Exception e) {
                System.out.printf("vertexToNormal: [%d] %s", frame, e.getMessage());
            }

            if (config.useTornado()) {

                vertexToNormal.schedule().waitOn();
                vertexToNormal.dumpTimes();

            } else {

                long start = System.nanoTime();
                GraphicsMath.vertex2normal(normal[refIndex], vertex[refIndex]);
                long end = System.nanoTime();
                System.out.printf("vertexToNormal: executime time=%f\n", TornadoUtilities.elapsedTimeInSeconds(start, end));

            }

            float maxULP = calculateULP(normal[refIndex], normalTruth[refIndex]);
            System.out.printf("vertexToNormal: [%d] max ulp=%f\n", frame, maxULP);

            if (maxULP > config.getMaxULP()) {
                errors++;
            }

            refIndex = (refIndex + 3) % 3;
        }

        if (errors > 0) {
            System.out.println(String.format("Found %d errors", errors));
        }
    }

    public static void main(String args[]) {
        Depth2Vertex tests = new Depth2Vertex();
        try {
            tests.setUp();
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        System.out.println("Depth to Vertex");
        tests.testDepth2vertex();

        System.out.println("Vertex to Normal");
        tests.testVertex2Normal();

        try {
            tests.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
