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
package kfusion;

import kfusion.algorithms.IterativeClosestPoint;
import uk.ac.manchester.tornado.api.collections.types.Float6;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.FloatSE3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;

public class SolveTest {

    public static void main(String[] args) {
        // Float6 v = new Float6(0.0004787097877f, 0.001456019769f, -0.001696595198f,
        // 0.001594305595f, 0.0002660461997f, 0.0004686777447f);
        float[] values = new float[] { -0.003683260176f, -0.007639631629f, -0.001128824428f, 0.003903858364f, -0.003509281203f, -0.004992492497f, 3212.803223f, -232.5692749f, -4758.245117f,
                -5163.686035f, 6703.038086f, -3705.914551f, 5589.393555f, -992.1624756f, -4401.467773f, 1114.671387f, 5879.70752f, 24602.80078f, 27276.56836f, -28787.75781f, 4049.014648f,
                32576.00391f, -31777.23047f, 1103.133301f, 35161.12109f, -5802.330078f, 10325.82129f };

        Float6 result = new Float6();

        IterativeClosestPoint.solve(result, values, 1);
        System.out.println(result.toString(FloatOps.fmt6e));

        Matrix4x4Float delta = new FloatSE3(result).toMatrix4();

        System.out.println(delta.toString(FloatOps.fmt4em));

    }

}
