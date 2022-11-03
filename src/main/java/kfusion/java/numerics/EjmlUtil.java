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

import org.ejml.simple.SimpleMatrix;

import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;

public class EjmlUtil {

    public static Matrix2DFloat toMatrixFloat(SimpleMatrix m) {
        Matrix2DFloat result = new Matrix2DFloat(m.numCols(), m.numRows());
        for (int i = 0; i < m.numRows(); i++) {
            for (int j = 0; j < m.numCols(); j++) {
                result.set(i, j, (float) m.get(i, j));
            }
        }
        return result;
    }

    public static SimpleMatrix toMatrix(Matrix2DFloat m) {
        SimpleMatrix result = new SimpleMatrix(m.getNumRows(), m.getNumColumns());
        for (int i = 0; i < m.getNumRows(); i++) {
            for (int j = 0; j < m.getNumColumns(); j++) {
                result.set(i, j, m.get(i, j));
            }
        }
        return result;
    }
}
