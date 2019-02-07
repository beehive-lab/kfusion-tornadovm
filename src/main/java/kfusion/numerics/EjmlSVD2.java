/*
 *    This file is part of Slambench-Java: A (serial) Java version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-java
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
package kfusion.numerics;

import static uk.ac.manchester.tornado.api.collections.types.VectorFloat.max;

import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import uk.ac.manchester.tornado.api.collections.types.MatrixFloat;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat;

/**
 * Singular Value Decomposition.
 * <P>
 * For an m-by-n matrix A with m >= n, the singular value decomposition is an
 * m-by-n orthogonal matrix U, an n-by-n diagonal matrix S, and an n-by-n
 * orthogonal matrix V so that A = U*S*V'.
 * <P>
 * The singular values, sigma[k] = S[k][k], are ordered so that sigma[0] >=
 * sigma[1] >= ... >= sigma[n-1].
 * <P>
 * The singular value decompostion always exists, so the constructor will never
 * fail. The matrix condition number and the effective numerical rank can be
 * computed from this decomposition.
 */

public strictfp class EjmlSVD2 {

    /*
     * ------------------------ Class variables ------------------------
     */

    private SimpleSVD svd;
    private SimpleMatrix m;
    private boolean expection;

    /*
     * ------------------------ Constructor ------------------------
     */

    /**
     * Construct the singular value decomposition Structure to access U, S and V.
     * 
     * @param Arg
     *            Rectangular matrix
     */
    public EjmlSVD2(MatrixFloat arg) {
        m = EjmlUtil.toMatrix(arg);
        // printMatrix(m);
        // System.out.println("Simple matrix");
        // m.print();
        try {
            svd = m.svd();
        } catch (RuntimeException e) {
            expection = true;
            System.out.println("burp");
        }
    }

    private static void printMatrix(SimpleMatrix m) {
        System.out.println();
        for (int y = 0; y < m.numRows(); y++) {
            for (int x = 0; x < m.numCols(); x++) {
                System.out.printf("%f ", m.get(y, x));
            }
            System.out.println();
        }

    }

    private final double[] toDoubleArray(float[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++)
            result[i] = (double) values[i];
        return result;
    }

    private final float[] toFloatArray(double[] values) {
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++)
            result[i] = (float) values[i];
        return result;
    }

    /*
     * ------------------------ Public Methods ------------------------
     */

    public boolean isValid() {
        return !expection;
    }

    /**
     * Return the left singular vectors
     * 
     * @return U
     */
    public MatrixFloat getU() {
        // svd.getU().print();
        return EjmlUtil.toMatrixFloat(svd.getU());
    }

    /**
     * Return the right singular vectors
     * 
     * @return V
     */
    public MatrixFloat getV() {
        return EjmlUtil.toMatrixFloat(svd.getV());
    }

    /**
     * Return the one-dimensional array of singular values
     * 
     * @return diagonal of S.
     */
    public float[] getSingularValues() {
        float[] results = new float[m.numRows()];
        for (int i = 0; i < m.numRows(); i++)
            results[i] = (float) svd.getSingleValue(i);

        return results;
    }

    /**
     * Return the diagonal matrix of singular values
     * 
     * @return S
     */
    public MatrixFloat getS() {
        return EjmlUtil.toMatrixFloat(svd.getW());
    }

    public MatrixFloat getSinv(float condition) {
        final MatrixFloat X = EjmlUtil.toMatrixFloat(svd.getW());
        for (int i = 0; i < X.M(); i++) {
            float value = X.get(i, i);
            if (value * condition <= X.get(0, 0))
                X.set(i, i, 0f);
            else
                X.set(i, i, 1.0f / value);
        }

        return X;
    }

    // /** Two norm
    // @return max(S)
    // */
    // public float norm2 () {
    // return (float) svd.norm2();
    // }
    //
    // /** Two norm condition number
    // @return max(S)/min(S)
    // */
    // public float cond () {
    // return (float) svd.cond();
    // }
    //
    // /** Effective numerical matrix rank
    // @return Number of nonnegligible singular values.
    // */
    // public int rank () {
    // return svd.rank();
    // }

    public float[] getPsuedoInverse(float condition) {
        // float[] s = getSingularValues();
        final VectorFloat s = EjmlUtil.toMatrixFloat(svd.getW()).diag();
        final float dMax = max(s);

        final float[] result = new float[s.size()];

        for (int i = 0; i < s.size(); i++) {
            result[i] = ((s.get(i) * condition < dMax) ? 1.0f / s.get(i) : 0f);
        }

        return result;
    }

    private static final long serialVersionUID = 1;
}
