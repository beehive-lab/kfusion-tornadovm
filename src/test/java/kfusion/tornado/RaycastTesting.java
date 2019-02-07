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

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import kfusion.java.algorithms.Raycast;
import kfusion.java.common.Utils;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.FloatingPointError;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.collections.types.VolumeShort2;

public class RaycastTesting {
	
    final private TornadoModel config = new TornadoModel();
    final private String FILE_PATH = "/Users/jamesclarkson/Downloads/kfusion_ut_data";

    final String raycast_prefix = "raycast_";

    private ImageFloat3 vOut;
    private ImageFloat3 nOut;
    private ImageFloat3 pos3D;
    private ImageFloat3 normal;
    private VolumeShort2 volume;
    private Matrix4x4Float view;
    private Float3 volumeDims;

    private float nearPlane;
    private float farPlane;
    private float step;
    private float largeStep;

    private TaskSchedule graph;

    @Before
    public void setUp() throws Exception {

        vOut = new ImageFloat3(320, 240);
        nOut = new ImageFloat3(320, 240);
        pos3D = new ImageFloat3(320, 240);
        normal = new ImageFloat3(320, 240);
        volume = new VolumeShort2(256, 256, 256);
        view = new Matrix4x4Float();
        volumeDims = new Float3(2f, 2f, 2f);

        float[] tmp = new float[1];
        Utils.loadData(String.format("%s/%snearPlane.in.%04d", FILE_PATH, raycast_prefix, 0), tmp);
        nearPlane = tmp[0];

        Utils.loadData(String.format("%s/%sfarPlane.in.%04d", FILE_PATH, raycast_prefix, 0), tmp);
        farPlane = tmp[0];

        Utils.loadData(String.format("%s/%sstep.in.%04d", FILE_PATH, raycast_prefix, 0), tmp);
        step = tmp[0];

        Utils.loadData(String.format("%s/%slargestep.in.%04d", FILE_PATH, raycast_prefix, 0), tmp);
        largeStep = tmp[0];

        System.out.printf("      step: %f\n", step);
        System.out.printf("large step: %f\n", largeStep);

        graph = new TaskSchedule("s0").streamIn(volume, view).task("raycast", Raycast::raycast, vOut, nOut, volume, volumeDims, view, nearPlane, farPlane, largeStep / 0.75f, step)
                .streamOut(vOut, nOut).mapAllTo(config.getTornadoDevice());

        // makeVolatile(volume,view);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRaycast() {

        final int[] frames = { 0 };

        int errors = 0;
        for (int frame = 0; frame < frames.length; frame++) {

            try {
                Utils.loadData(String.format("%s/%spos3D.out.%04d", FILE_PATH, raycast_prefix, frame), pos3D.asBuffer());
                Utils.loadData(String.format("%s/%snormal.out.%04d", FILE_PATH, raycast_prefix, frame), normal.asBuffer());
                Utils.loadData(String.format("%s/%svolume.in.%04d", FILE_PATH, raycast_prefix, frame), volume.asBuffer());
                Utils.loadData(String.format("%s/%sview.in.%04d", FILE_PATH, raycast_prefix, frame), view.asBuffer());
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

            if (config.useTornado()) {
                graph.schedule().waitOn();
                graph.dumpTimes();
            } else {

                Raycast.raycast(vOut, nOut, volume, volumeDims, view, nearPlane, farPlane, largeStep, step);

            }

            System.out.printf("[%d, %d]: vertex=%s, ref=%s\n", 11, 15, vOut.get(11, 15).toString(), pos3D.get(11, 15).toString());
            final FloatingPointError vertexError = vOut.calculateULP(pos3D);
            System.out.printf("frame[%d]: vertex errors: %s\n", frame, vertexError.toString());

            final FloatingPointError normalError = nOut.calculateULP(normal);
            System.out.printf("frame[%d]: normal errors: %s\n", frame, normalError.toString());

            if (vertexError.getMaxUlp() > config.getMaxULP() || normalError.getMaxUlp() > config.getMaxULP()) {
                errors++;
            }
        }

        if (errors > 0) {
            fail("Found errors");
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

                final float ulpFactor = Float3.findULPDistance(v, r);
                averageULP += ulpFactor;
                minULP = Math.min(ulpFactor, minULP);
                maxULP = Math.max(ulpFactor, maxULP);

            }
        }

        averageULP /= (float) value.X() * value.Y();
        System.out.printf("image: mean ulp=%f, min=%f, max=%f\n", averageULP, minULP, maxULP);

        return maxULP;
    }

    public static void main(String[] args) {
        RaycastTesting testing = new RaycastTesting();

        try {
            testing.setUp();

            testing.testRaycast();
            testing.tearDown();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
