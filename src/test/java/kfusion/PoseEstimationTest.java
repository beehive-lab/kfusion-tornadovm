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
package kfusion;

import static org.junit.Assert.fail;

import java.nio.FloatBuffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import kfusion.algorithms.IterativeClosestPoint;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.collections.types.Float6;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.FloatSE3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.collections.matrix.MatrixMath;

public class PoseEstimationTest {

    final private String FILE_PATH = "/Users/jamesclarkson/Downloads/kfusion_ut_data";
    final private String solve_prefix = "solve_";

    final private float EPSILON = 1e-5f;

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test() {

        int errors = 0;
        final int frames = 506;

        final float[] values = new float[32];
        Matrix4x4Float pose = new Matrix4x4Float();
        Matrix4x4Float newPose = new Matrix4x4Float();
        Matrix4x4Float newPoseRef = new Matrix4x4Float();

        // IterativeClosestPoint icp = new
        // IterativeClosestPoint(dist_threshold,normal_threshold,0f,null);
        for (int i = 1; i < frames; i++) {

            try {
                Utils.loadData(String.format("%s/%svalues.in.%04d", FILE_PATH, solve_prefix, i), FloatBuffer.wrap(values));
                Utils.loadData(String.format("%s/%spose.in.%04d", FILE_PATH, solve_prefix, i), pose.asBuffer());
                Utils.loadData(String.format("%s/%spose.out.%04d", FILE_PATH, solve_prefix, i), newPoseRef.asBuffer());
            } catch (Exception e) {
                System.out.printf("[%04d]: failed to load data (%s)\n", i, e.getMessage());
                continue;
            }

            final Float6 result = new Float6();

            IterativeClosestPoint.solve(result, values, 1);

            Matrix4x4Float delta = new FloatSE3(result).toMatrix4();

            MatrixMath.sgemm(delta, pose, newPose);

            if (!TornadoMath.isEqualTol(newPose.asBuffer().array(), newPoseRef.asBuffer().array(), EPSILON)) {
                System.out.printf("[%04d]: arrays do not match:\n", i);
                System.out.println("calc:\n" + newPose.toString(FloatOps.fmt4em));
                System.out.println("ref :\n" + newPoseRef.toString(FloatOps.fmt4em));

                // System.out.println("result:\n" + result.toString());
                // System.out.println("delta :\n" + delta.toString());
                // System.out.println("pose :\n" + pose.toString());
                errors++;
                // break;
            }
        }
        if (errors > 0) {
            double pct = ((double) errors / ((double) frames)) * 100.0;
            System.out.printf("Found %d errors ( %.2f %%)\n", errors, pct);
            fail("Errors found");
        }
    }

    public static void main(String args[]) {
        PoseEstimationTest tests = new PoseEstimationTest();
        try {
            tests.setUp();
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        tests.test();

        try {
            tests.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
