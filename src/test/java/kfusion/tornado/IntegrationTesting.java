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
package kfusion.tornado;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import kfusion.Utils;
import kfusion.algorithms.Integration;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float2;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.collections.types.Short2;
import uk.ac.manchester.tornado.api.collections.types.VolumeShort2;

public class IntegrationTesting {

    final private TornadoModel config = new TornadoModel();
    final private String FILE_PATH = "/Users/jamesclarkson/Downloads/kfusion_ut_data";
    final String integrate_prefix = "integrate_";

    private VolumeShort2 vol;

    private VolumeShort2 volTruth;
    private ImageFloat depth;

    private Matrix4x4Float invTrack;

    private Matrix4x4Float K;

    private float mu;
    private float maxweight;
    private Float3 volumeDims;

    private final float[] integrationDims = { 2f, 2f, 2f };

    private TaskSchedule graph;

    @Before
    public void setUp() throws Exception {
        vol = new VolumeShort2(256, 256, 256);
        volTruth = new VolumeShort2(256, 256, 256);
        volumeDims = new Float3(2f, 2f, 2f);
        depth = new ImageFloat(320, 240);
        invTrack = new Matrix4x4Float();
        K = new Matrix4x4Float();

        initVolume(vol, new Float2(1f, 0f));

        float[] tmp = new float[1];
        Utils.loadData(String.format("%s/%s%s.%04d", FILE_PATH, integrate_prefix, "mu.in", 0), tmp);
        mu = tmp[0];

        Utils.loadData(String.format("%s/%s%s.%04d", FILE_PATH, integrate_prefix, "maxweight.in", 0), tmp);
        maxweight = tmp[0];

        // final PrebuiltTask integrate = TaskUtils.createTask("integrate",
        // "integrate",
        // "./opencl/integrate.cl",
        // new Object[]{depth, invTrack, K, volumeDims, vol, mu, maxweight},
        // new Access[]{Access.READ, Access.READ, Access.READ, Access.READ,
        // Access.READ_WRITE, Access.READ, Access.READ},
        // config.getTornadoDevice(),
        // new int[]{vol.X(), vol.Y()});
        graph = new TaskSchedule("s0").streamIn(vol, depth, invTrack, K).task("integrate", Integration::integrate, depth, invTrack, K, volumeDims, vol, mu, maxweight)
                // .task(integrate)
                .streamOut(vol).mapAllTo(config.getTornadoDevice());

        // integrateTask.mapTo(EXTERNAL_GPU);
        // integrateTask.getStack().getEvent().waitOn();
        // makeVolatile(vol,depth,invTrack,K);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testIntegrate() {
        boolean foundErrors = false;

        // 4 valid inputs vvvv
        for (int i = 0; i < 4; i++) {
            System.out.printf("integrate: test frame %d\n", i);

            try {
                Utils.loadData(String.format("%s/%s%s.%04d", FILE_PATH, integrate_prefix, "vol.in", i), vol.asBuffer());
                Utils.loadData(String.format("%s/%s%s.%04d", FILE_PATH, integrate_prefix, "vol.out", i), volTruth.asBuffer());
                Utils.loadData(String.format("%s/%s%s.%04d", FILE_PATH, integrate_prefix, "depth.in", i), depth.asBuffer());
                Utils.loadData(String.format("%s/%s%s.%04d", FILE_PATH, integrate_prefix, "invtrack.in", i), invTrack.asBuffer());
                Utils.loadData(String.format("%s/%s%s.%04d", FILE_PATH, integrate_prefix, "k.in", i), K.asBuffer());
            } catch (Exception e) {
                fail("Unable to load data: " + e.getMessage());
            }

            if (config.useTornado()) {
                graph.schedule().waitOn();
                graph.dumpTimes();
            } else {
                long start = System.nanoTime();
                Integration.integrate(depth, invTrack, K, volumeDims, vol, mu, maxweight);
                long end = System.nanoTime();
                System.out.printf("task: %f \n", (end - start) * 1e-9f);
            }

            int errors = 0;
            for (int z = 0; z < 256; z++) {
                for (int y = 0; y < 256; y++) {
                    for (int x = 0; x < 256; x++) {

                        final Short2 v = vol.get(x, y, z);
                        final Short2 vRef = volTruth.get(x, y, z);

                        if (!Short2.isEqual(v, vRef)) {
                            // System.out.printf("[%d,%d,%d] error: %s !=
                            // %s\n",x,y,z,v.toString(),vRef.toString());
                            errors++;
                        } else {
                            // System.out.printf("ok: %d != %d\n",v.get(ii),vRef.get(ii));
                        }
                    }
                }
            }

            if (errors > 0) {
                double pct = ((double) errors / (256.0 * 256.0 * 256.0 * 2.0)) * 100.0;
                System.out.printf("errors: %d (%.2f %%)\n", errors, pct);

                /*
                 * try { Utils.dumpData(FILE_PATH + "/integrate.dat",vol.array); } catch
                 * (Exception e) { e.printStackTrace(); }
                 */
                foundErrors = true;
            } else {
                System.out.printf("no errors\n");
            }
        }

        if (foundErrors) {
            fail("Found errors");
        }

    }

    // TODO simplify this
    private void initVolume(final VolumeShort2 volume, final Float2 val) {
        final Short2 tmp = new Short2();
        tmp.setX((short) (val.getX() * 32766.0f));
        tmp.setY((short) val.getY());

        for (int z = 0; z < volume.Z(); z++) {
            for (int y = 0; y < volume.Y(); y++) {
                for (int x = 0; x < volume.X(); x++) {
                    volume.set(x, y, z, tmp);
                }
            }
        }

    }

    public static void main(String[] args) {
        IntegrationTesting testing = new IntegrationTesting();

        try {
            testing.setUp();

            testing.testIntegrate();
            testing.tearDown();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
