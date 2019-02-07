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

import org.ejml.simple.SimpleMatrix;

import uk.ac.manchester.tornado.api.collections.types.MatrixFloat;

public class EjmlUtil {

    public static MatrixFloat toMatrixFloat(SimpleMatrix m) {
        MatrixFloat result = new MatrixFloat(m.numCols(), m.numRows());
        for (int i = 0; i < m.numRows(); i++) {
            for (int j = 0; j < m.numCols(); j++) {
                result.set(i, j, (float) m.get(i, j));
            }
        }
        return result;
    }

    public static SimpleMatrix toMatrix(MatrixFloat m) {
        SimpleMatrix result = new SimpleMatrix(m.M(), m.N());
        for (int i = 0; i < m.M(); i++) {
            for (int j = 0; j < m.N(); j++) {
                result.set(i, j, (double) m.get(i, j));
            }
        }
        return result;
    }
}
