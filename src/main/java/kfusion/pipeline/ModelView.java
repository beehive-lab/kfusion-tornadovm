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
package kfusion.pipeline;

import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;

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
