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
package kfusion.java.algorithms;

import static uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat.transpose;

import kfusion.java.common.KfusionConfig;
import kfusion.java.numerics.Constants;
import kfusion.java.numerics.EjmlSVD2;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.types.Float2;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float6;
import uk.ac.manchester.tornado.api.collections.types.Float8;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.FloatSE3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat8;
import uk.ac.manchester.tornado.api.collections.types.Int2;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat;
import uk.ac.manchester.tornado.matrix.MatrixMath;

public class IterativeClosestPoint {

    private static void makeJTJ(final Matrix2DFloat a, final float[] vals, final int offset) {
        a.set(0, 0, vals[0 + offset]);
        a.set(0, 1, vals[1 + offset]);
        a.set(0, 2, vals[2 + offset]);
        a.set(0, 3, vals[3 + offset]);
        a.set(0, 4, vals[4 + offset]);
        a.set(0, 5, vals[5 + offset]);

        a.set(1, 1, vals[6 + offset]);
        a.set(1, 2, vals[7 + offset]);
        a.set(1, 3, vals[8 + offset]);
        a.set(1, 4, vals[9 + offset]);
        a.set(1, 5, vals[10 + offset]);

        a.set(2, 2, vals[11 + offset]);
        a.set(2, 3, vals[12 + offset]);
        a.set(2, 4, vals[13 + offset]);
        a.set(2, 5, vals[14 + offset]);

        a.set(3, 3, vals[15 + offset]);
        a.set(3, 4, vals[16 + offset]);
        a.set(3, 5, vals[17 + offset]);

        a.set(4, 4, vals[18 + offset]);
        a.set(4, 5, vals[19 + offset]);

        a.set(5, 5, vals[20 + offset]);

        // assume that a is symmetric???
        for (int r = 1; r < 6; r++) {
            for (int c = 0; c < r; c++) {
                a.set(r, c, a.get(c, r));
            }
        }

    }

    public static void reduce1(final float[] output, final ImageFloat8 input) {
        final int numThreads = output.length / 32;

        for (int tid = 0; tid < numThreads; tid++) {

            final float[] sums = new float[32];
            for (int i = 0; i < sums.length; i++) {
                sums[i] = 0f;
            }

            final int numElements = input.X() * input.Y();
            for (int i = tid; i < numElements; i += numThreads) {
                reduceInner(sums, input, i);
            }

            for (int i = 0; i < 32; i++) {
                output[(tid * 32) + i] = sums[i];
            }

        }

    }

    public static void reduce2(final float[] output, final float[] input) {
        final int numThreads = 32;

        for (int tid = 0; tid < numThreads; tid++) {
            float sum = 0f;
            for (int i = tid; i < input.length; i += numThreads) {
                sum += input[i];
            }

            output[tid] = sum;
        }

    }

    public static void reduceInner(final float[] sums, final ImageFloat8 trackingResults, int resultIndex) {

        final int jtj = 7;
        final int info = 28;

        final Float8 value = trackingResults.get(resultIndex);
        final int result = (int) value.getS7();
        final float error = value.getS6();

        if (result < 1) {
            sums[info + 1] += (result == -4) ? 1 : 0;
            sums[info + 2] += (result == -5) ? 1 : 0;
            sums[info + 3] += (result > -4) ? 1 : 0;
            return;
        }

        // float base[0] += error^2
        sums[0] += (error * error);

        // System.out.printf("row error: error=%.4e, acc=%.4e\n",error,base.get(0));
        // Float6 base(+1) += row.scale(error)
        for (int i = 0; i < 6; i++) {
            sums[i + 1] += error * value.get(i);
        }

        // is this jacobian transpose jacobian?
        sums[jtj + 0] += (value.get(0) * value.get(0));
        sums[jtj + 1] += (value.get(0) * value.get(1));
        sums[jtj + 2] += (value.get(0) * value.get(2));
        sums[jtj + 3] += (value.get(0) * value.get(3));

        sums[jtj + 4] += (value.get(0) * value.get(4));
        sums[jtj + 5] += (value.get(0) * value.get(5));

        sums[jtj + 6] += (value.get(1) * value.get(1));
        sums[jtj + 7] += (value.get(1) * value.get(2));
        sums[jtj + 8] += (value.get(1) * value.get(3));
        sums[jtj + 9] += (value.get(1) * value.get(4));

        sums[jtj + 10] += (value.get(1) * value.get(5));

        sums[jtj + 11] += (value.get(2) * value.get(2));
        sums[jtj + 12] += (value.get(2) * value.get(3));
        sums[jtj + 13] += (value.get(2) * value.get(4));
        sums[jtj + 14] += (value.get(2) * value.get(5));

        sums[jtj + 15] += (value.get(3) * value.get(3));
        sums[jtj + 16] += (value.get(3) * value.get(4));
        sums[jtj + 17] += (value.get(3) * value.get(5));

        sums[jtj + 18] += (value.get(4) * value.get(4));
        sums[jtj + 19] += (value.get(4) * value.get(5));

        sums[jtj + 20] += (value.get(5) * value.get(5));

        sums[info]++;

    }

    public static void reduce(final float[] globalSums, final ImageFloat8 trackingResults) {

        final float[] sums = new float[32];
        for (int i = 0; i < sums.length; i++) {
            sums[i] = 0f;
        }

        final int jtj = 7;
        final int info = 28;

        for (int y = 0; y < trackingResults.Y(); y++) {
            for (int x = 0; x < trackingResults.X(); x++) {

                final Float8 row = trackingResults.get(x, y);
                final int result = (int) row.getS7();
                final float error = row.getS6();

                if (result < 1) {
                    sums[info + 1] += (result == -4) ? 1 : 0;
                    sums[info + 2] += (result == -5) ? 1 : 0;
                    sums[info + 3] += (result > -4) ? 1 : 0;
                    continue;
                }

                // float base[0] += error^2
                sums[0] += (error * error);

                // System.out.printf("row error: error=%.4e, acc=%.4e\n",error,base.get(0));
                // Float6 base(+1) += row.scale(error)
                for (int i = 0; i < 6; i++) {
                    sums[i + 1] += error * row.get(i);
                }

                // is this jacobian transpose jacobian?
                sums[jtj + 0] += (row.get(0) * row.get(0));
                sums[jtj + 1] += (row.get(0) * row.get(1));
                sums[jtj + 2] += (row.get(0) * row.get(2));
                sums[jtj + 3] += (row.get(0) * row.get(3));

                sums[jtj + 4] += (row.get(0) * row.get(4));
                sums[jtj + 5] += (row.get(0) * row.get(5));

                sums[jtj + 6] += (row.get(1) * row.get(1));
                sums[jtj + 7] += (row.get(1) * row.get(2));
                sums[jtj + 8] += (row.get(1) * row.get(3));
                sums[jtj + 9] += (row.get(1) * row.get(4));

                sums[jtj + 10] += (row.get(1) * row.get(5));

                sums[jtj + 11] += (row.get(2) * row.get(2));
                sums[jtj + 12] += (row.get(2) * row.get(3));
                sums[jtj + 13] += (row.get(2) * row.get(4));
                sums[jtj + 14] += (row.get(2) * row.get(5));

                sums[jtj + 15] += (row.get(3) * row.get(3));
                sums[jtj + 16] += (row.get(3) * row.get(4));
                sums[jtj + 17] += (row.get(3) * row.get(5));

                sums[jtj + 18] += (row.get(4) * row.get(4));
                sums[jtj + 19] += (row.get(4) * row.get(5));

                sums[jtj + 20] += (row.get(5) * row.get(5));

                sums[info]++;
            }
        }

        for (int i = 0; i < 32; i++) {
            globalSums[i] += sums[i];
        }

    }

    public static void solve(final Float6 result, final float[] vals, int offset) {
        final Matrix2DFloat C = new Matrix2DFloat(6, 6);
        final Float6 b = new Float6();

        for (int i = 0; i < 6; i++) {
            b.set(i, vals[i + offset]);
        }
        makeJTJ(C, vals, offset + 6);

        // SVD
        // TODO remove dependency on EJML
        final EjmlSVD2 svd = new EjmlSVD2(C);

        if (svd.isValid()) {
            // svd backsub
            final Matrix2DFloat V = svd.getV();
            final Matrix2DFloat U = svd.getU();
            transpose(U);

            final Matrix2DFloat inv = svd.getSinv((float) 1e6);

            final Float6 t1 = new Float6();
            MatrixMath.multiply(t1, U, b);

            final Float6 t2 = new Float6();
            MatrixMath.multiply(t2, inv, t1);

            MatrixMath.multiply(result, V, t2);

        } else {
            System.err.println("invalid SVD");
        }
    }

    public static void trackPose(final ImageFloat8 results, final ImageFloat3 verticies, final ImageFloat3 normals, final ImageFloat3 referenceVerticies, final ImageFloat3 referenceNormals,
            final Matrix4x4Float currentPose, final Matrix4x4Float view, final float distanceThreshold, final float normalThreshold) {

        final Float8 NO_INPUT = new Float8(0f, 0f, 0f, 0f, 0f, 0f, 0f, Constants.BLACK);
        final Float8 NOT_IN_IMAGE = new Float8(0f, 0f, 0f, 0f, 0f, 0f, 0f, Constants.RED);
        final Float8 NO_CORRESPONDENCE = new Float8(0f, 0f, 0f, 0f, 0f, 0f, 0f, Constants.GREEN);
        final Float8 TOO_FAR = new Float8(0f, 0f, 0f, 0f, 0f, 0f, 0f, Constants.BLUE);
        final Float8 WRONG_NORMAL = new Float8(0f, 0f, 0f, 0f, 0f, 0f, 0f, Constants.YELLOW);

        for (int y = 0; y < results.Y(); y++) {
            for (int x = 0; x < results.X(); x++) {

                if (normals.get(x, y).getX() == Constants.INVALID) {
                    results.set(x, y, NO_INPUT);
                } else {

                    // rotate + translate projected vertex
                    final Float3 projectedVertex = GraphicsMath.rigidTransform(currentPose, verticies.get(x, y));

                    // rotate + translate projected position
                    final Float3 projectedPos = GraphicsMath.rigidTransform(view, projectedVertex);

                    final Float2 projectedPixel = Float2.add(Float2.mult(projectedPos.asFloat2(), 1f / projectedPos.getZ()), 0.5f);

                    boolean isNotInImage = (projectedPixel.getX() < 0) || (projectedPixel.getX() > (referenceVerticies.X() - 1)) || (projectedPixel.getY() < 0)
                            || (projectedPixel.getY() > (referenceVerticies.Y() - 1));

                    if (isNotInImage) {
                        results.set(x, y, NOT_IN_IMAGE);
                    } else {

                        final Int2 refPixel = new Int2((int) projectedPixel.getX(), (int) projectedPixel.getY());
                        final Float3 referenceNormal = referenceNormals.get(refPixel.getX(), refPixel.getY());

                        if (referenceNormal.getX() == Constants.INVALID) {
                            results.set(x, y, NO_CORRESPONDENCE);
                        } else {

                            final Float3 diff = Float3.sub(referenceVerticies.get(refPixel.getX(), refPixel.getY()), projectedVertex);

                            if (Float3.length(diff) > distanceThreshold) {
                                results.set(x, y, TOO_FAR);
                            } else {

                                final Float3 projectedNormal = GraphicsMath.rotate(currentPose, normals.get(x, y));

                                if (Float3.dot(projectedNormal, referenceNormal) < normalThreshold) {
                                    results.set(x, y, WRONG_NORMAL);
                                } else {

                                    final Float3 b = Float3.cross(projectedVertex, referenceNormal);

                                    final Float8 tracking = new Float8(referenceNormal.getX(), referenceNormal.getY(), referenceNormal.getZ(), b.getX(), b.getY(), b.getZ(),
                                            Float3.dot(referenceNormal, diff), Constants.GREY);

                                    results.set(x, y, tracking);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static <T extends KfusionConfig> boolean estimateNewPose(final T config, final TrackingResult result, final ImageFloat8 trackingResults, final Matrix4x4Float currentPose,
            final float icpThreshold) {
        final float[] icpResults = new float[32];
        reduce(icpResults, trackingResults);
        result.resultImage = trackingResults;
        return estimateNewPose(config, result, icpResults, currentPose, icpThreshold);
    }

    public static <T extends KfusionConfig> boolean estimateNewPose(final T config, final TrackingResult result, final float[] icpResults, final Matrix4x4Float currentPose, final float icpThreshold) {

        result.error = icpResults[0];
        result.tracked = icpResults[28];
        result.tooFar = icpResults[29];
        result.wrongNormal = icpResults[30];
        result.other = icpResults[31];

        if (config.debug()) {
            System.out.printf("\tvalues: %s\n", new VectorFloat(icpResults).toString("%e "));
            // System.out.printf("values{1,27}: %s\n", icpResults.subVector(1, 21)
            // .toString("%e "));
        }

        // System.out.printf("icpResults[1:22] -> %s\n",icpResults.subVector(1,
        // 21).toString());
        solve(result.x, icpResults, 1);

        if (config.debug()) {
            System.out.printf("\tx: %s\n", result.x.toString(FloatOps.FMT_6_E));
        }

        final Matrix4x4Float delta = new FloatSE3(result.x).toMatrix4();

        if (config.debug()) {
            System.out.printf("*delta:\n%s\n", delta.toString(FloatOps.FMT_4_EM));
            System.out.printf("*current pose:\n%s\n", currentPose.toString());
        }

        MatrixMath.sgemm(delta, currentPose, result.pose);

        if (config.debug()) {
            System.out.printf("*newPose:\n%s\n", result.pose.toString());
        }

        // System.out.printf("length(x): %s,
        // %.4e\n",result.x.toString(FloatOps.fmt6e),Float6.length(result.x));
        return (Float6.length(result.x) < icpThreshold);
    }

}
