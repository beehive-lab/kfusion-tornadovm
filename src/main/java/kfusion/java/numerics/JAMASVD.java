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
package kfusion.java.numerics;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;

public class JAMASVD {

    final SingularValueDecomposition svd;
    final double scale;

    public JAMASVD(Matrix2DFloat m) {
        final Matrix matrix = new Matrix(m.getNumRows(), m.getNumColumns());
        double minValue = 0f;
        for (int row = 0; row < m.getNumRows(); row++)
            for (int col = 0; col < m.getNumRows(); col++) {
                double value = m.get(row, col);
                minValue = Math.min(value, minValue);
                matrix.set(row, col, value);
            }
        scale = 1.0;// Math.getExponent(minValue);
        matrix.times(1.0 / scale);
        svd = new SingularValueDecomposition(matrix);
    }

    public Matrix2DFloat getU() {
        return toMatrixFloat(svd.getU().times(scale));
    }

    public Matrix2DFloat getV() {
        return toMatrixFloat(svd.getV().times(scale));
    }

    public Matrix2DFloat getS() {
        return toMatrixFloat(svd.getS().times(scale));
    }

    private Matrix2DFloat toMatrixFloat(Matrix m) {
        final Matrix2DFloat result = new Matrix2DFloat(m.getRowDimension(), m.getColumnDimension());
        for (int row = 0; row < result.getNumRows(); row++)
            for (int col = 0; col < result.getNumColumns(); col++)
                result.set(row, col, (float) m.get(row, col));
        return result;
    }

    public Matrix2DFloat getSinv(float condition) {
        final Matrix2DFloat X = toMatrixFloat(svd.getS());
        for (int i = 0; i < X.getNumRows(); i++) {
            float value = X.get(i, i);
            if (value * condition <= X.get(0, 0))
                X.set(i, i, 0f);
            else
                X.set(i, i, 1.0f / value);
        }
        return X;
    }

}
