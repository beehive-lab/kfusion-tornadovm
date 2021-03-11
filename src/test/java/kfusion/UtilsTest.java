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
package kfusion;

import java.nio.FloatBuffer;

import kfusion.java.common.Utils;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat;

public class UtilsTest {

    public static void main(String[] args) {

        final String type = args[0];
        final String filename = args[1];

        try {
            switch (type) {
                case "VectorFloat32":
                    final VectorFloat v32 = new VectorFloat(32);
                    Utils.loadData(filename, v32.asBuffer());
                    System.out.println(v32.toString("%.4e"));
                    break;
                case "Float4":
                    final Float4 v4 = new Float4();

                    Utils.loadData(filename, v4.asBuffer());
                    System.out.println(v4.toString(FloatOps.FMT_4_EM));
                    break;
                case "Matrix4x4Float":
                    final Matrix4x4Float m4x4 = new Matrix4x4Float();

                    Utils.loadData(filename, m4x4.asBuffer());
                    System.out.println(m4x4.toString(FloatOps.FMT_4_EM));
                    break;
                case "ImageFloat320x240":
                    final ImageFloat img320_240 = new ImageFloat(320, 240);

                    Utils.loadData(filename, img320_240.asBuffer());
                    System.out.println(img320_240.summerise());
                    break;
                case "Float":
                    FloatBuffer fb = FloatBuffer.allocate(1);
                    Utils.loadData(filename, fb);
                    fb.flip();
                    System.out.printf("%.4e\n", fb.get());
                    break;
                default:
                    System.err.println("Unknown type: " + type);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
