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
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
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



    public static void mapReduce(final FloatArray output, final FloatArray input, final int numElements) {
        final int numThreads = output.getSize() / 32;

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

    /**
     * Middle stage of the on-device ICP reduction: collapses {@code chunk} consecutive per-thread
     * blocks into one, in parallel over (group, slot). This is what lets stage one run with enough
     * threads to fill the GPU - with only 1024 threads (32 warps on 128 SMs) mapReduce was 87% of all
     * GPU time.
     */
    public static void reducePartials(final FloatArray output, final FloatArray input, final int chunk) {
        final int groups = output.getSize() / 32;
        for (@Parallel int index = 0; index < groups * 32; index++) {
            final int group = index / 32;
            final int slot = index - (group * 32);
            float sum = 0f;
            for (int k = 0; k < chunk; k++) {
                sum += input.get((((group * chunk) + k) * 32) + slot);
            }
            output.set((group * 32) + slot, sum);
        }
    }

    /**
     * Second stage of the on-device ICP reduction: sums the per-thread partial blocks written by
     * {@link #mapReduce} so that only the final 32 floats have to be copied back to the host, instead
     * of {@code reductionSize * 32} floats plus a serial host-side sum.
     */
    public static void reduceFinal(final FloatArray output, final FloatArray partials, final int numThreads) {
        for (@Parallel int slot = 0; slot < 32; slot++) {
            float sum = 0f;
            for (int thread = 0; thread < numThreads; thread++) {
                sum += partials.get((thread * 32) + slot);
            }
            output.set(slot, sum);
        }
    }

    /**
     * {@link #mapReduce} with the convergence guard of an unrolled ICP iteration: once the level has
     * converged the threads return immediately instead of re-reducing an unchanged tracking image.
     */
    public static void mapReduceGuarded(final FloatArray output, final FloatArray input, final int numElements, final FloatArray control) {
        final int numThreads = output.getSize() / 32;

        for (@Parallel int i = 0; i < numThreads; i++) {
            if (control.get(IcpSolver.CONTROL_CONVERGED) == 0f && control.get(IcpSolver.CONTROL_SOLVE_FAILED) == 0f) {
                final int startIndex = i * 32;
                for (int j = 0; j < 32; j++) {
                    output.set(startIndex + j, 0f);
                }

                for (int j = i; j < numElements; j += numThreads) {
                    reduceValues(output, startIndex, input, j);
                }
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


    public static void reduceValues(final FloatArray sums, final int startIndex, final FloatArray trackingResults, int resultIndex) {

        final int jtj = startIndex + 7;
        final int info = startIndex + 28;

        final int offset = resultIndex * TRACK_STRIDE;
        final int result = (int) trackingResults.get(offset + 7);
        final float error = trackingResults.get(offset + 6);

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


        sums.set(startIndex + 0 + 1, sums.get(startIndex + 0 + 1) + (error * trackingResults.get(offset + 0)));
        sums.set(startIndex + 1 + 1, sums.get(startIndex + 1 + 1) + (error * trackingResults.get(offset + 1)));
        sums.set(startIndex + 2 + 1, sums.get(startIndex + 2 + 1) + (error * trackingResults.get(offset + 2)));
        sums.set(startIndex + 3 + 1, sums.get(startIndex + 3 + 1) + (error * trackingResults.get(offset + 3)));
        sums.set(startIndex + 4 + 1, sums.get(startIndex + 4 + 1) + (error * trackingResults.get(offset + 4)));
        sums.set(startIndex + 5 + 1, sums.get(startIndex + 5 + 1) + (error * trackingResults.get(offset + 5)));

        // is this jacobian transpose jacobian?
        sums.set(jtj + 0, sums.get(jtj + 0) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 0)));
        sums.set(jtj + 1, sums.get(jtj + 1) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 1)));
        sums.set(jtj + 2, sums.get(jtj + 2) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 2)));
        sums.set(jtj + 3, sums.get(jtj + 3) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 3)));
        sums.set(jtj + 4, sums.get(jtj + 4) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 4)));
        sums.set(jtj + 5, sums.get(jtj + 5) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 5)));

        sums.set(jtj + 6, sums.get(jtj + 6) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 1)));
        sums.set(jtj + 7, sums.get(jtj + 7) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 2)));
        sums.set(jtj + 8, sums.get(jtj + 8) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 3)));
        sums.set(jtj + 9, sums.get(jtj + 9) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 4)));
        sums.set(jtj + 10, sums.get(jtj + 10) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 5)));

        sums.set(jtj + 11, sums.get(jtj + 11) + (trackingResults.get(offset + 2) * trackingResults.get(offset + 2)));
        sums.set(jtj + 12, sums.get(jtj + 12) + (trackingResults.get(offset + 2) * trackingResults.get(offset + 3)));
        sums.set(jtj + 13, sums.get(jtj + 13) + (trackingResults.get(offset + 2) * trackingResults.get(offset + 4)));
        sums.set(jtj + 14, sums.get(jtj + 14) + (trackingResults.get(offset + 2) * trackingResults.get(offset + 5)));

        sums.set(jtj + 15, sums.get(jtj + 15) + (trackingResults.get(offset + 3) * trackingResults.get(offset + 3)));
        sums.set(jtj + 16, sums.get(jtj + 16) + (trackingResults.get(offset + 3) * trackingResults.get(offset + 4)));
        sums.set(jtj + 17, sums.get(jtj + 17) + (trackingResults.get(offset + 3) * trackingResults.get(offset + 5)));

        sums.set(jtj + 18, sums.get(jtj + 18) + (trackingResults.get(offset + 4) * trackingResults.get(offset + 4)));
        sums.set(jtj + 19, sums.get(jtj + 19) + (trackingResults.get(offset + 4) * trackingResults.get(offset + 5)));

        sums.set(jtj + 20, sums.get(jtj + 20) + (trackingResults.get(offset + 5) * trackingResults.get(offset + 5)));

        sums.set(info, sums.get(info) + 1);
    }

    public static void reduce(final FloatArray globalSums, final FloatArray trackingResults, final int numElements) {

        final FloatArray sums = new FloatArray(32);
        for (int i = 0; i < sums.getSize(); i++) {
            sums.set(i, 0f);
        }

        final int jtj = 7;
        final int info = 28;

        for (int element = 0; element < numElements; element++) {
            {
                final int offset = element * TRACK_STRIDE;
                final int result = (int) trackingResults.get(offset + 7);
                final float error = trackingResults.get(offset + 6);

                if (result < 1) {
                    sums.set(info + 1, sums.get(info + 1) + ((result == -4) ? 1 : 0));
                    sums.set(info + 2, sums.get(info + 2) + ((result == -5) ? 1 : 0));
                    sums.set(info + 3, sums.get(info + 3) + ((result > -4) ? 1 : 0));
                    continue;
                }

                sums.set(0, sums.get(0) + (error * error));

                for (int i = 0; i < 6; i++) {
                    sums.set(i + 1, (sums.get(i + 1) + error * trackingResults.get(offset + i)));
                }

                // is this jacobian transpose jacobian?
                sums.set(jtj, sums.get(jtj) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 0)));
                sums.set(jtj + 1, sums.get(jtj + 1) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 1)));
                sums.set(jtj + 2, sums.get(jtj + 2) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 2)));
                sums.set(jtj + 3, sums.get(jtj + 3) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 3)));

                sums.set(jtj + 4, sums.get(jtj + 4) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 4)));
                sums.set(jtj + 5, sums.get(jtj + 5) + (trackingResults.get(offset + 0) * trackingResults.get(offset + 5)));

                sums.set(jtj + 6, sums.get(jtj + 6) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 1)));
                sums.set(jtj + 7, sums.get(jtj + 7) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 2)));
                sums.set(jtj + 8, sums.get(jtj + 8) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 3)));
                sums.set(jtj + 9, sums.get(jtj + 9) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 4)));

                sums.set(jtj + 10, sums.get(jtj + 10) + (trackingResults.get(offset + 1) * trackingResults.get(offset + 5)));

                sums.set(jtj + 11, sums.get(jtj + 11) + (trackingResults.get(offset + 2) * trackingResults.get(offset + 2)));
                sums.set(jtj + 12, sums.get(jtj + 12) + (trackingResults.get(offset + 2) * trackingResults.get(offset + 3)));
                sums.set(jtj + 13, sums.get(jtj + 13) + (trackingResults.get(offset + 2) * trackingResults.get(offset + 4)));
                sums.set(jtj + 14, sums.get(jtj + 14) + (trackingResults.get(offset + 2) * trackingResults.get(offset + 5)));

                sums.set(jtj + 15, sums.get(jtj + 15) + (trackingResults.get(offset + 3) * trackingResults.get(offset + 3)));
                sums.set(jtj + 16, sums.get(jtj + 16) + (trackingResults.get(offset + 3) * trackingResults.get(offset + 4)));
                sums.set(jtj + 17, sums.get(jtj + 17) + (trackingResults.get(offset + 3) * trackingResults.get(offset + 5)));

                sums.set(jtj + 18, sums.get(jtj + 18) + (trackingResults.get(offset + 4) * trackingResults.get(offset + 4)));
                sums.set(jtj + 19, sums.get(jtj + 19) + (trackingResults.get(offset + 4) * trackingResults.get(offset + 5)));

                sums.set(jtj + 20, sums.get(jtj + 20) + (trackingResults.get(offset + 5) * trackingResults.get(offset + 5)));

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

    /** Number of floats stored per pixel of the tracking result: 6 Jacobian terms, error, status. */
    public static final int TRACK_STRIDE = 8;

    private static void storeStatus(final FloatArray results, final int base, final float status) {
        results.set(base, 0f);
        results.set(base + 1, 0f);
        results.set(base + 2, 0f);
        results.set(base + 3, 0f);
        results.set(base + 4, 0f);
        results.set(base + 5, 0f);
        results.set(base + 6, 0f);
        results.set(base + 7, status);
    }

    /**
     * ICP correspondence search. The result is a flat array of {@link #TRACK_STRIDE} floats per pixel
     * rather than an {@code ImageFloat8}: the CUDA backend has no 8-wide vector type ("does not support
     * vector width 8"), so a packed array is the only portable layout.
     */
    public static void trackPose(final FloatArray results, final int resultsWidth, final int resultsHeight, final ImageFloat3 verticies, final ImageFloat3 normals,
            final ImageFloat3 referenceVerticies, final ImageFloat3 referenceNormals, final Matrix4x4Float currentPose, final Matrix4x4Float view, final float distanceThreshold,
            final float normalThreshold) {

        for (@Parallel int y = 0; y < resultsHeight; y++) {
            for (@Parallel int x = 0; x < resultsWidth; x++) {

                final int base = (x + (y * resultsWidth)) * TRACK_STRIDE;

                if (normals.get(x, y).getX() == Constants.INVALID) {
                    storeStatus(results, base, Constants.BLACK);
                } else {

                    // rotate + translate projected vertex
                    final Float3 projectedVertex = GraphicsMath.rigidTransform(currentPose, verticies.get(x, y));

                    // rotate + translate projected position
                    final Float3 projectedPos = GraphicsMath.rigidTransform(view, projectedVertex);

                    final Float2 projectedPixel = Float2.add(Float2.mult(projectedPos.asFloat2(), 1f / projectedPos.getZ()), 0.5f);

                    boolean isNotInImage = (projectedPixel.getX() < 0) || (projectedPixel.getX() > (referenceVerticies.X() - 1)) || (projectedPixel.getY() < 0)
                            || (projectedPixel.getY() > (referenceVerticies.Y() - 1));

                    if (isNotInImage) {
                        storeStatus(results, base, Constants.RED);
                    } else {

                        final Int2 refPixel = new Int2((int) projectedPixel.getX(), (int) projectedPixel.getY());

                        final Float3 referenceNormal = referenceNormals.get(refPixel.getX(), refPixel.getY());

                        if (referenceNormal.getX() == Constants.INVALID) {
                            storeStatus(results, base, Constants.GREEN);
                        } else {

                            final Float3 diff = Float3.sub(referenceVerticies.get(refPixel.getX(), refPixel.getY()), projectedVertex);

                            if (Float3.length(diff) > distanceThreshold) {
                                storeStatus(results, base, Constants.BLUE);
                            } else {

                                final Float3 projectedNormal = GraphicsMath.rotate(currentPose, normals.get(x, y));

                                if (Float3.dot(projectedNormal, referenceNormal) < normalThreshold) {
                                    storeStatus(results, base, Constants.YELLOW);
                                } else {

                                    final Float3 b = Float3.cross(projectedVertex, referenceNormal);

                                    results.set(base, referenceNormal.getX());
                                    results.set(base + 1, referenceNormal.getY());
                                    results.set(base + 2, referenceNormal.getZ());
                                    results.set(base + 3, b.getX());
                                    results.set(base + 4, b.getY());
                                    results.set(base + 5, b.getZ());
                                    results.set(base + 6, Float3.dot(referenceNormal, diff));
                                    results.set(base + 7, Constants.GREY);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static <T extends KfusionConfig> boolean estimateNewPose(final T config, final TrackingResult result, final FloatArray trackingResults, final int numElements,
            final Matrix4x4Float currentPose, final float icpThreshold) {
        final FloatArray icpResults = new FloatArray(32);
        reduce(icpResults, trackingResults, numElements);
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
