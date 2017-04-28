/* 
 * Copyright 2017 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kfusion.tornado.algorithms;

import tornado.api.Parallel;
import tornado.collections.graphics.GraphicsMath;
import tornado.collections.types.*;

import static tornado.collections.types.Float3.length;
import static tornado.collections.types.Float3.normalise;

public class Raycast {

    private static final float INVALID = -2;

    public static final void raycast(ImageFloat3 verticies, ImageFloat3 normals, VolumeShort2 volume, Float3 volumeDims, Matrix4x4Float view, float nearPlane, float farPlane, float largeStep, float smallStep) {

        // use volume model to generate a reference view by raycasting ...
        for (@Parallel int y = 0; y < verticies.Y(); y++) {
            for (@Parallel int x = 0; x < verticies.X(); x++) {
                final Float4 hit = GraphicsMath.raycastPoint(volume,
                        volumeDims, x, y, view, nearPlane,
                        farPlane, smallStep, largeStep);

                final Float3 normal;
                final Float3 position;
                if (hit.getW() > 0f) {
                    position = hit.asFloat3();

                    final Float3 surfNorm = VolumeOps.grad(volume,
                            volumeDims, position);

                    if (length(surfNorm) != 0) {
                        normal = normalise(surfNorm);
                    } else {
                        normal = new Float3(INVALID, 0f, 0f);
                    }
                } else {
                    normal = new Float3(INVALID, 0f, 0f);
                    position = new Float3();
                }

                verticies.set(x, y, position);
                normals.set(x, y, normal);

            }
        }
    }
}
