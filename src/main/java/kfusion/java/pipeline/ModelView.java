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
package kfusion.java.pipeline;

import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;

public class ModelView {
    private final ImageFloat3 normals;
    private final ImageFloat3 verticies;
    private final Matrix4x4Float pose;
    private final ImageFloat depths;

    public ModelView(final ImageFloat3 verticies, final ImageFloat3 normals, final ImageFloat depths, final Matrix4x4Float pose) {
        this.normals = normals;
        this.verticies = verticies;
        this.depths = depths;
        this.pose = pose;
    }

    public ImageFloat3 getNormals() {
        return normals;
    }

    public ImageFloat3 getVerticies() {
        return verticies;
    }

    public Matrix4x4Float getPose() {
        return pose;
    }

    public ImageFloat getDepths() {
        return depths;
    }
}
