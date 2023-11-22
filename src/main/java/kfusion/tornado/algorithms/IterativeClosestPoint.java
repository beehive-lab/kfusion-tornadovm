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
package kfusion.tornado.algorithms;

import kfusion.java.algorithms.TrackingResult;
import kfusion.java.common.KfusionConfig;
import kfusion.java.numerics.Constants;
import kfusion.java.numerics.EjmlSVD2;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat8;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.api.types.vectors.Int2;
import uk.ac.manchester.tornado.matrix.MatrixMath;

public class IterativeClosestPoint {

    private static void makeJTJ(final Matrix2DFloat a, final FloatArray vals, final int offset) {
        a.set(0, 0, vals.get(0 + offset));
        a.set(0, 1, vals.get(1 + offset));
        a.set(0, 2, vals.get(2 + offset));
        a.set(0, 3, vals.get(3 + offset));
        a.set(0, 4, vals.get(4 + offset));
        a.set(0, 5, vals.get(5 + offset));

        a.set(1, 1, vals.get(6 + offset));
        a.set(1, 2, vals.get(7 + offset));
        a.set(1, 3, vals.get(8 + offset));
        a.set(1, 4, vals.get(9 + offset));
        a.set(1, 5, vals.get(10 + offset));

        a.set(2, 2, vals.get(11 + offset));
        a.set(2, 3, vals.get(12 + offset));
        a.set(2, 4, vals.get(13 + offset));
        a.set(2, 5, vals.get(14 + offset));

        a.set(3, 3, vals.get(15 + offset));
        a.set(3, 4, vals.get(16 + offset));
        a.set(3, 5, vals.get(17 + offset));

        a.set(4, 4, vals.get(18 + offset));
        a.set(4, 5, vals.get(19 + offset));

        a.set(5, 5, vals.get(20 + offset));

        // assume that a is symmetric???
        for (int r = 1; r < 6; r++) {
            for (int c = 0; c < r; c++) {
                a.set(r, c, a.get(c, r));
            }
        }
    }

    public static void mapInitData(final FloatArray output, final ImageFloat8 input) {
        final int numThreads = output.getSize() / 32;
        for (@Parallel int i = 0; i < numThreads; i++) {
            final int startIndex = i * 32;
            for (int j = 0; j < 32; j++) {
                output.set(startIndex + j, 0.0f);
            }
        }
    }

    public static void reduceData(final FloatArray output, final ImageFloat8 input) {

        int offset = 32;

        final int numThreads = output.getSize() / offset;
        final int numElements = input.X() * input.Y();

        for (@Parallel int i = 0; i < numThreads; i++) {
            final int startIndex = i * offset;
            for (int j = i; j < numElements; j += numThreads) {
                reduceArrayValues(output, startIndex, input, j);
                // reduceValues(output, startIndex, input, j);
            }
        }
    }

    public static void mapReduce(final FloatArray output, final ImageFloat8 input) {
        final int numThreads = output.getSize() / 32;
        final int numElements = input.X() * input.Y();

        for (@Parallel int i = 0; i < numThreads; i++) {
            final int startIndex = i * 32;
            for (int j = 0; j < 32; j++) {
                output.set(startIndex + j, 0f);
            }

            for (int j = i; j < numElements; j += numThreads) {
                reduceValues(output, startIndex, input, j);
            }
        }
    }

    public static void reduceIntermediate(final FloatArray output, final FloatArray input) {

        final int elementSize = 32;
        final int numDestElements = output.getSize() / elementSize;
        final int numSrcElements = input.getSize() / elementSize;

        for (@Parallel int i = 0; i < numDestElements; i++) {
            final int startIndex = i * elementSize;
            final FloatArray result = new FloatArray(elementSize);

            // copy first block of values
            for (int j = 0; j < elementSize; j++) {
                result.set(j, (i < numSrcElements) ? input.get(startIndex + j) : 0);
            }

            // reduce the remainder
            for (int j = i + numDestElements; j < numSrcElements; j += numDestElements) {
                final int startElement = j * elementSize;
                for (int k = 0; k < elementSize; k++) {
                    result.set(k, result.get(k) + input.get(startElement + k));
                }
            }

            // copy out to main memory
            for (int j = 0; j < elementSize; j++) {
                output.set(startIndex + j, result.get(j));
            }

        }
    }

    private static FloatArray getPlainArray(Float8 input) {
        FloatArray value = new FloatArray(8);
        value.set(0, input.getS0());
        value.set(1, input.getS1());
        value.set(2, input.getS2());
        value.set(3, input.getS3());
        value.set(4, input.getS4());
        value.set(5, input.getS5());
        value.set(6, input.getS6());
        value.set(7, input.getS7());
        return value;
    }

    private static void reduceSumWithError(@Reduce final FloatArray sums, float error, int startIndex, int N, FloatArray value) {
        sums.set(startIndex, sums.get(startIndex) + (error * error));
        for (@Parallel int i = 0; i < N; i++) {
            sums.set(startIndex + i + 1, sums.get(startIndex + i + 1) + (error * value.get(i)));
        }
    }

    private static void reduceAllValues(final FloatArray sums, int N, FloatArray value, int base) {
        for (int i = 0; i < N; i++) {
            int counter = 0;
            for (int j = i; j < N; j++) {
                sums.set(base + counter, sums.get(base + counter) + (value.get(i) * value.get(j)));
                counter++;
            }
        }
    }

    public static void reduceArrayValues(final FloatArray sums, final int startIndex, final ImageFloat8 trackingResults, int resultIndex) {

        final int base = startIndex + 7;
        final int info = startIndex + 28;
        final int N = 6;

        final FloatArray value = getPlainArray(trackingResults.get(resultIndex));

        final int result = (int) value.get(7);
        final float error = value.get(6);

        if (result < 1) {
            sums.set(info + 1, sums.get(info + 1) + ((result == -4) ? 1.0f : 0.0f));
            sums.set(info + 2, sums.get(info + 2) + ((result == -5) ? 1.0f : 0f));
            sums.set(info + 3, sums.get(info + 3) + ((result > -4) ? 1.0f : 0.0f));
            return;
        }

        reduceSumWithError(sums, error, startIndex, N, value);
        reduceAllValues(sums, N, value, base);

        sums.set(info, sums.get(info) + 1);
    }

    public static void reduceValues(final FloatArray sums, final int startIndex, final ImageFloat8 trackingResults, int resultIndex) {

        final int jtj = startIndex + 7;
        final int info = startIndex + 28;

        Float8 value = trackingResults.get(resultIndex);
        final int result = (int) value.getS7();
        final float error = value.getS6();

        if (result < 1) {
            int condA = ((result == -4) ? 1 : 0);
            int condB = ((result == -5) ? 1 : 0);
            int condC = ((result > -4) ? 1 : 0);
            sums.set(info + 1, sums.get(info + 1) + condA);
            sums.set(info + 2, sums.get(info + 2) + condB);
            sums.set(info + 3, sums.get(info + 3) + condC);
            return;
        }

        sums.set(startIndex, sums.get(startIndex) + (error * error));


        sums.set(startIndex + 0 + 1, sums.get(startIndex + 0 + 1) + (error * value.getS0()));
        sums.set(startIndex + 1 + 1, sums.get(startIndex + 1 + 1) + (error * value.getS1()));
        sums.set(startIndex + 2 + 1, sums.get(startIndex + 2 + 1) + (error * value.getS2()));
        sums.set(startIndex + 3 + 1, sums.get(startIndex + 3 + 1) + (error * value.getS3()));
        sums.set(startIndex + 4 + 1, sums.get(startIndex + 4 + 1) + (error * value.getS4()));
        sums.set(startIndex + 5 + 1, sums.get(startIndex + 5 + 1) + (error * value.getS5()));

        // is this jacobian transpose jacobian?
        sums.set(jtj + 0, sums.get(jtj + 0) + (value.getS0() * value.getS0()));
        sums.set(jtj + 1, sums.get(jtj + 1) + (value.getS0() * value.getS1()));
        sums.set(jtj + 2, sums.get(jtj + 2) + (value.getS0() * value.getS2()));
        sums.set(jtj + 3, sums.get(jtj + 3) + (value.getS0() * value.getS3()));
        sums.set(jtj + 4, sums.get(jtj + 4) + (value.getS0() * value.getS4()));
        sums.set(jtj + 5, sums.get(jtj + 1) + (value.getS0() * value.getS5()));

        sums.set(jtj + 6, sums.get(jtj + 6) + (value.getS1() * value.getS1()));
        sums.set(jtj + 7, sums.get(jtj + 1) + (value.getS1() * value.getS2()));
        sums.set(jtj + 8, sums.get(jtj + 8) + (value.getS1() * value.getS3()));
        sums.set(jtj + 9, sums.get(jtj + 9) + (value.getS1() * value.getS4()));
        sums.set(jtj + 10, sums.get(jtj + 10) + (value.getS1() * value.getS5()));

        sums.set(jtj + 11, sums.get(jtj + 11) + (value.getS2() * value.getS2()));
        sums.set(jtj + 12, sums.get(jtj + 12) + (value.getS2() * value.getS3()));
        sums.set(jtj + 13, sums.get(jtj + 13) + (value.getS2() * value.getS4()));
        sums.set(jtj + 14, sums.get(jtj + 1) + (value.getS2() * value.getS5()));

        sums.set(jtj + 15, sums.get(jtj + 15) + (value.getS3() * value.getS3()));
        sums.set(jtj + 16, sums.get(jtj + 16) + (value.getS3() * value.getS4()));
        sums.set(jtj + 17, sums.get(jtj + 17) + (value.getS3() * value.getS5()));

        sums.set(jtj + 18, sums.get(jtj + 18) + (value.getS4() * value.getS4()));
        sums.set(jtj + 19, sums.get(jtj + 19) + (value.getS4() * value.getS5()));

        sums.set(jtj + 20, sums.get(jtj + 20) + (value.getS5() * value.getS5()));

        sums.set(info, sums.get(info) + 1);
    }

    public static void reduce(final FloatArray globalSums, final ImageFloat8 trackingResults) {

        final FloatArray sums = new FloatArray(32);
        for (int i = 0; i < sums.getSize(); i++) {
            sums.set(i, 0f);
        }

        final int jtj = 7;
        final int info = 28;

        for (int y = 0; y < trackingResults.Y(); y++) {
            for (int x = 0; x < trackingResults.X(); x++) {

                final Float8 row = trackingResults.get(x, y);
                final int result = (int) row.getS7();
                final float error = row.getS6();

                if (result < 1) {
                    sums.set(info + 1, sums.get(info + 1) + ((result == -4) ? 1 : 0));
                    sums.set(info + 2, sums.get(info + 2) + ((result == -5) ? 1 : 0));
                    sums.set(info + 3, sums.get(info + 3) + ((result > -4) ? 1 : 0));
                    continue;
                }

                sums.set(0, sums.get(0) + (error * error));

                for (int i = 0; i < 6; i++) {
                    sums.set(i + 1, (sums.get(i + 1) + error * row.get(i)));
                }

                // is this jacobian transpose jacobian?
                sums.set(jtj, sums.get(jtj) + (row.get(0) * row.get(0)));
                sums.set(jtj + 1, sums.get(jtj + 1) + (row.get(0) * row.get(1)));
                sums.set(jtj + 2, sums.get(jtj + 2) + (row.get(0) * row.get(2)));
                sums.set(jtj + 3, sums.get(jtj + 3) + (row.get(0) * row.get(3)));

                sums.set(jtj + 4, sums.get(jtj + 4) + (row.get(0) * row.get(4)));
                sums.set(jtj + 5, sums.get(jtj + 5) + (row.get(0) * row.get(5)));

                sums.set(jtj + 6, sums.get(jtj + 6) + (row.get(1) * row.get(1)));
                sums.set(jtj + 7, sums.get(jtj + 7) + (row.get(1) * row.get(2)));
                sums.set(jtj + 8, sums.get(jtj + 8) + (row.get(1) * row.get(3)));
                sums.set(jtj + 9, sums.get(jtj + 9) + (row.get(1) * row.get(4)));

                sums.set(jtj + 10, sums.get(jtj + 10) + (row.get(1) * row.get(5)));

                sums.set(jtj + 11, sums.get(jtj + 11) + (row.get(2) * row.get(2)));
                sums.set(jtj + 12, sums.get(jtj + 12) + (row.get(2) * row.get(3)));
                sums.set(jtj + 13, sums.get(jtj + 13) + (row.get(2) * row.get(4)));
                sums.set(jtj + 14, sums.get(jtj + 14) + (row.get(2) * row.get(5)));

                sums.set(jtj + 15, sums.get(jtj + 15) + (row.get(3) * row.get(3)));
                sums.set(jtj + 16, sums.get(jtj + 16) + (row.get(3) * row.get(4)));
                sums.set(jtj + 17, sums.get(jtj + 17) + (row.get(3) * row.get(5)));

                sums.set(jtj + 18, sums.get(jtj + 18) + (row.get(4) * row.get(4)));
                sums.set(jtj + 19, sums.get(jtj + 19) + (row.get(4) * row.get(5)));

                sums.set(jtj + 20, sums.get(jtj + 20) + (row.get(5) * row.get(5)));

                sums.set(info, sums.get(info) + 1);
            }
        }

        for (int i = 0; i < 32; i++) {
            globalSums.set(i, globalSums.get(i) + sums.get(i));
        }

    }

    public static void solve(final FloatArray result, final FloatArray vals, int offset) {
        final Matrix2DFloat C = new Matrix2DFloat(6, 6);
        final FloatArray b = new FloatArray(6);

        for (int i = 0; i < 6; i++) {
            b.set(i, vals.get(i + offset));
        }
        makeJTJ(C, vals, offset + 6);

        // TODO remove dependency on EJML
        final EjmlSVD2 svd = new EjmlSVD2(C);

        if (svd.isValid()) {
            // svd backsub
            final Matrix2DFloat V = svd.getV();
            final Matrix2DFloat U = svd.getU();
            Matrix2DFloat.transpose(U);
            final Matrix2DFloat inv = svd.getSinv((float) 1e6);
            final FloatArray t1 = new FloatArray(6);
            MatrixMath.multiply(t1, U, b);
            final FloatArray t2 = new FloatArray(6);
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

        for (@Parallel int y = 0; y < results.Y(); y++) {
            for (@Parallel int x = 0; x < results.X(); x++) {

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
                                            Float3.dot(referenceNormal, diff), (float) Constants.GREY);

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
        final FloatArray icpResults = new FloatArray(32);
        reduce(icpResults, trackingResults);
        result.resultImage = trackingResults;
        return estimateNewPose(config, result, icpResults, currentPose, icpThreshold);
    }

    public static <T extends KfusionConfig> boolean estimateNewPose(final T config, final TrackingResult result, final FloatArray icpResults, final Matrix4x4Float currentPose, final float icpThreshold) {

        result.error = icpResults.get(0);
        result.tracked = icpResults.get(28);
        result.tooFar = icpResults.get(29);
        result.wrongNormal = icpResults.get(30);
        result.other = icpResults.get(31);

        if (config.debug()) {
            System.out.printf("\tvalues: %s\n", new VectorFloat(icpResults).toString("%e "));
        }

        solve(result.x, icpResults, 1);

        if (config.debug()) {
            System.out.printf("\tx: %s\n", stringRepresentation(result.x));
        }

        final Matrix4x4Float delta = new FloatSE3(result.x).toMatrix4();

        if (config.debug()) {
            System.out.printf("*delta:\n%s\n", delta.toString(FloatOps.FMT_4_EM));
            System.out.printf("*current pose:\n%s\n", currentPose.toString());
        }

        MatrixMath.sgemm(delta, currentPose, result.pose);

        if (config.debug()) {
            System.out.printf("*newPose:\n%s\n", result.pose);
        }

        return (length(result.x) < icpThreshold);
    }

    public static float length(FloatArray value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static float dot(FloatArray a, FloatArray b) {
        float result = 0f;
        final FloatArray m = mult(a, b);
        for (int i = 0; i < a.getSize(); i++) {
            result += m.get(i);
        }
        return result;
    }

    public static FloatArray mult(FloatArray a, FloatArray b) {
        final FloatArray result = new FloatArray(a.getSize());
        for (int i = 0; i < result.getSize(); i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    private static String stringRepresentation(FloatArray x) {
        String values = "";
        for (int i = 0; i < x.getSize(); i++) {
            values = values + " " + x.get(i);
        }
        return values;
    }

}
