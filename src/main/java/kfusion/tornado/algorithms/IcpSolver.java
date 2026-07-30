/*
 *  This file is part of Tornado-KFusion: A Java version of the KFusion computer vision
 *  algorithm running on TornadoVM.
 *  URL: https://github.com/beehive-lab/kfusion-tornadovm
 *
 *  Copyright (c) 2026, APT Group, Department of Computer Science,
 *  The University of Manchester
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

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;

/**
 * Solves the 6x6 ICP system and applies the pose update <em>on the device</em>, so that the iterations
 * of one pyramid level no longer need a blocking device-to-host round trip each.
 *
 * <p>
 * The host implementation ({@link IterativeClosestPoint#solve}) uses an SVD pseudo-inverse with
 * conditioning, which is not reproducible in a kernel. This solver uses a Cholesky factorisation
 * instead, which agrees with the pseudo-inverse for well-conditioned systems and detects the
 * ill-conditioned ones through a non-positive pivot. In that case it sets
 * {@link #CONTROL_SOLVE_FAILED} and leaves the pose untouched, so the host can redo that iteration
 * with the SVD path.
 * </p>
 *
 * <p>
 * All scratch space is passed in as device buffers rather than allocated in the kernel, and every
 * kernel of the iteration is guarded by {@link #CONTROL_CONVERGED} so that a converged level costs
 * only empty launches instead of a host synchronisation.
 * </p>
 */
public class IcpSolver {

    /** Set to 1 once the pose update is smaller than the ICP threshold. */
    public static final int CONTROL_CONVERGED = 0;
    /** Set to 1 when Cholesky hit a non-positive pivot and the host has to solve this iteration. */
    public static final int CONTROL_SOLVE_FAILED = 1;
    /** Norm of the last pose update. */
    public static final int CONTROL_UPDATE_NORM = 2;
    /** Number of iterations that actually ran, for the host-side statistics. */
    public static final int CONTROL_ITERATIONS = 3;
    public static final int CONTROL_SIZE = 4;

    /** Scratch layout: 36 floats for the matrix, 6 for the right-hand side, 6 for the solution. */
    private static final int SCRATCH_MATRIX = 0;
    private static final int SCRATCH_RHS = 36;
    private static final int SCRATCH_SOLUTION = 42;
    public static final int SCRATCH_SIZE = 48;

    private static final float PIVOT_EPSILON = 1e-12f;

    /**
     * Guard used by every kernel of an unrolled ICP iteration: once the level has converged, or the
     * host has to take over, the remaining kernels return immediately.
     */
    public static boolean isActive(final FloatArray control) {
        return control.get(CONTROL_CONVERGED) == 0f && control.get(CONTROL_SOLVE_FAILED) == 0f;
    }

    /**
     * Cholesky solve of the 6x6 normal equations followed by the SE3 update of {@code pose}. Mirrors
     * {@code new FloatSE3(x).toMatrix4()} and {@code MatrixMath.sgemm(delta, pose, pose)} on the host.
     */
    public static void solveAndUpdatePose(final FloatArray icpResult, final Matrix4x4Float pose, final FloatArray control, final FloatArray scratch, final float icpThreshold) {

        for (@Parallel int t = 0; t < 1; t++) {
            if (control.get(CONTROL_CONVERGED) == 0f && control.get(CONTROL_SOLVE_FAILED) == 0f) {

                // ----- build the symmetric system: b = icpResult[1..6], JtJ = icpResult[7..27] -----
                for (int i = 0; i < 6; i++) {
                    scratch.set(SCRATCH_RHS + i, icpResult.get(1 + i));
                }

                int index = 7;
                for (int row = 0; row < 6; row++) {
                    for (int col = row; col < 6; col++) {
                        final float value = icpResult.get(index);
                        index++;
                        scratch.set(SCRATCH_MATRIX + (row * 6) + col, value);
                        scratch.set(SCRATCH_MATRIX + (col * 6) + row, value);
                    }
                }

                // ----- Cholesky factorisation in place: A = L * L^T -----
                boolean positiveDefinite = true;
                for (int j = 0; j < 6; j++) {
                    float diagonal = scratch.get(SCRATCH_MATRIX + (j * 6) + j);
                    for (int k = 0; k < j; k++) {
                        final float ljk = scratch.get(SCRATCH_MATRIX + (j * 6) + k);
                        diagonal = diagonal - (ljk * ljk);
                    }
                    if (diagonal <= PIVOT_EPSILON) {
                        positiveDefinite = false;
                    } else {
                        final float ljj = TornadoMath.sqrt(diagonal);
                        scratch.set(SCRATCH_MATRIX + (j * 6) + j, ljj);
                        for (int i = j + 1; i < 6; i++) {
                            float value = scratch.get(SCRATCH_MATRIX + (i * 6) + j);
                            for (int k = 0; k < j; k++) {
                                value = value - (scratch.get(SCRATCH_MATRIX + (i * 6) + k) * scratch.get(SCRATCH_MATRIX + (j * 6) + k));
                            }
                            scratch.set(SCRATCH_MATRIX + (i * 6) + j, value / ljj);
                        }
                    }
                }

                if (!positiveDefinite) {
                    control.set(CONTROL_SOLVE_FAILED, 1f);
                } else {
                    // ----- forward substitution L y = b, then back substitution L^T x = y -----
                    for (int i = 0; i < 6; i++) {
                        float value = scratch.get(SCRATCH_RHS + i);
                        for (int k = 0; k < i; k++) {
                            value = value - (scratch.get(SCRATCH_MATRIX + (i * 6) + k) * scratch.get(SCRATCH_SOLUTION + k));
                        }
                        scratch.set(SCRATCH_SOLUTION + i, value / scratch.get(SCRATCH_MATRIX + (i * 6) + i));
                    }
                    for (int i = 5; i >= 0; i--) {
                        float value = scratch.get(SCRATCH_SOLUTION + i);
                        for (int k = i + 1; k < 6; k++) {
                            value = value - (scratch.get(SCRATCH_MATRIX + (k * 6) + i) * scratch.get(SCRATCH_SOLUTION + k));
                        }
                        scratch.set(SCRATCH_SOLUTION + i, value / scratch.get(SCRATCH_MATRIX + (i * 6) + i));
                    }

                    // ----- SE3 exponential of the solution, same three regimes as FloatSE3.exp -----
                    final float mu0 = scratch.get(SCRATCH_SOLUTION);
                    final float mu1 = scratch.get(SCRATCH_SOLUTION + 1);
                    final float mu2 = scratch.get(SCRATCH_SOLUTION + 2);
                    final float w0 = scratch.get(SCRATCH_SOLUTION + 3);
                    final float w1 = scratch.get(SCRATCH_SOLUTION + 4);
                    final float w2 = scratch.get(SCRATCH_SOLUTION + 5);

                    final float one6th = 1f / 6f;
                    final float one20th = 1f / 20f;
                    final float thetaSq = (w0 * w0) + (w1 * w1) + (w2 * w2);

                    // cross(w, muLo)
                    final float cross0 = (w1 * mu2) - (w2 * mu1);
                    final float cross1 = (w2 * mu0) - (w0 * mu2);
                    final float cross2 = (w0 * mu1) - (w1 * mu0);

                    float a;
                    float b;
                    float translation0;
                    float translation1;
                    float translation2;

                    if (thetaSq < 1e-8f) {
                        a = 1f - (one6th * thetaSq);
                        b = 0.5f;
                        translation0 = mu0 + (cross0 * 0.5f);
                        translation1 = mu1 + (cross1 * 0.5f);
                        translation2 = mu2 + (cross2 * 0.5f);
                    } else {
                        float c;
                        if (thetaSq < 1e-6f) {
                            c = one6th * (1f - (one20th * thetaSq));
                            a = 1f - (thetaSq * c);
                            b = 0.5f - (0.25f * one6th * thetaSq);
                        } else {
                            final float theta = TornadoMath.sqrt(thetaSq);
                            final float invTheta = 1f / theta;
                            a = TornadoMath.sin(theta) * invTheta;
                            b = (1f - TornadoMath.cos(theta)) * (invTheta * invTheta);
                            c = (1f - a) * (invTheta * invTheta);
                        }
                        // cross(w, cross(w, muLo))
                        final float wcp0 = (w1 * cross2) - (w2 * cross1);
                        final float wcp1 = (w2 * cross0) - (w0 * cross2);
                        final float wcp2 = (w0 * cross1) - (w1 * cross0);
                        translation0 = mu0 + (cross0 * b) + (wcp0 * c);
                        translation1 = mu1 + (cross1 * b) + (wcp1 * c);
                        translation2 = mu2 + (cross2 * b) + (wcp2 * c);
                    }

                    // Rodrigues rotation, matching rodriguesSo3Exp
                    final float r00 = 1f - (b * ((w1 * w1) + (w2 * w2)));
                    final float r11 = 1f - (b * ((w0 * w0) + (w2 * w2)));
                    final float r22 = 1f - (b * ((w0 * w0) + (w1 * w1)));
                    final float r01 = (b * (w0 * w1)) - (a * w2);
                    final float r10 = (b * (w0 * w1)) + (a * w2);
                    final float r02 = (b * (w0 * w2)) + (a * w1);
                    final float r20 = (b * (w0 * w2)) - (a * w1);
                    final float r12 = (b * (w1 * w2)) - (a * w0);
                    final float r21 = (b * (w1 * w2)) + (a * w0);

                    // ----- pose = delta * pose, written back in place -----
                    for (int col = 0; col < 4; col++) {
                        final float p0 = pose.get(0, col);
                        final float p1 = pose.get(1, col);
                        final float p2 = pose.get(2, col);
                        final float p3 = pose.get(3, col);

                        final float n0 = (r00 * p0) + (r01 * p1) + (r02 * p2) + (translation0 * p3);
                        final float n1 = (r10 * p0) + (r11 * p1) + (r12 * p2) + (translation1 * p3);
                        final float n2 = (r20 * p0) + (r21 * p1) + (r22 * p2) + (translation2 * p3);

                        pose.set(0, col, n0);
                        pose.set(1, col, n1);
                        pose.set(2, col, n2);
                        pose.set(3, col, p3);
                    }

                    // ----- convergence test, same criterion as the host loop -----
                    final float norm = TornadoMath.sqrt((mu0 * mu0) + (mu1 * mu1) + (mu2 * mu2) + (w0 * w0) + (w1 * w1) + (w2 * w2));
                    control.set(CONTROL_UPDATE_NORM, norm);
                    control.set(CONTROL_ITERATIONS, control.get(CONTROL_ITERATIONS) + 1f);
                    if (norm < icpThreshold) {
                        control.set(CONTROL_CONVERGED, 1f);
                    }
                }
            }
        }
    }
}
