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
package kfusion.java.algorithms;

import kfusion.tornado.algorithms.GraphicsMath;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Int2;
import uk.ac.manchester.tornado.api.types.vectors.Int3;
import uk.ac.manchester.tornado.api.types.vectors.Short2;
import uk.ac.manchester.tornado.api.types.volumes.VolumeShort2;

public class Integration {

	/**
	 * Tornado Version of Integrate Function
	 */
    public static void integrate(ImageFloat filteredDepthImage, Matrix4x4Float invTrack, Matrix4x4Float K, Float3 volumeDims, VolumeShort2 volume, float mu, float maxWeight) {
        final Float3 tmp = new Float3(0f, 0f, volumeDims.getZ() / (float) volume.Z());

        final Float3 integrateDelta = GraphicsMath.rotate(invTrack, tmp);
        final Float3 cameraDelta = GraphicsMath.rotate(K, integrateDelta);

        for (int y = 0; y < volume.Y(); y++) {
            for (int x = 0; x < volume.X(); x++) {

                final Int3 pix = new Int3(x, y, 0);
                Float3 pos = GraphicsMath.rigidTransform(invTrack, pos(volume, volumeDims, pix));
                Float3 cameraX = GraphicsMath.rigidTransform(K, pos);

                for (int z = 0; z < volume.Z(); z++, pos = Float3.add(pos, integrateDelta), cameraX = Float3.add(cameraX, cameraDelta)) {

                    if (pos.getZ() < 0.0001f) // arbitrary near plane constant
                        continue;

                    final Float2 pixel = new Float2((cameraX.getX() / cameraX.getZ()) + 0.5f, (cameraX.getY() / cameraX.getZ()) + 0.5f);

                    if ((pixel.getX() < 0) || (pixel.getX() > (filteredDepthImage.X() - 1)) || (pixel.getY() < 0) || (pixel.getY() > (filteredDepthImage.Y() - 1)))
                        continue;

                    final Int2 px = new Int2((int) pixel.getX(), (int) pixel.getY());

                    final float depth = filteredDepthImage.get(px.getX(), px.getY());

                    if (depth == 0)
                        continue;

                    final float diff = (depth - cameraX.getZ()) * TornadoMath.sqrt(1f + FloatOps.sq(pos.getX() / pos.getZ()) + FloatOps.sq(pos.getY() / pos.getZ()));

                    if (diff > -mu) {

                        final float sdf = TornadoMath.min(1f, diff / mu);

                        final Short2 inputValue = volume.get(x, y, z);
                        final Float2 constantValue1 = new Float2(0.00003051944088f, 1f);
                        final Float2 constantValue2 = new Float2(32766.0f, 1f);

                        final Float2 data = Float2.mult(new Float2(inputValue.getX(), inputValue.getY()), constantValue1);

                        final float dx = TornadoMath.clamp(((data.getY() * data.getX()) + sdf) / (data.getY() + 1f), -1f, 1f);
                        final float dy = TornadoMath.min(data.getY() + 1f, maxWeight);

                        final Float2 floatValue = Float2.mult(new Float2(dx, dy), constantValue2);
                        final Short2 outputValue = new Short2((short) floatValue.getX(), (short) floatValue.getY());

                        volume.set(x, y, z, outputValue);
                    }
                }
            }
        }
    }

    final private static Float3 pos(final VolumeShort2 volume, final Float3 volumeDims, final Int3 p) {
        return new Float3(((((float) p.getX()) + 0.5f) * ((float) volumeDims.getX())) / ((float) volume.X()), ((((float) p.getY()) + 0.5f) * ((float) volumeDims.getY())) / ((float) volume.Y()),
                ((((float) p.getZ()) + 0.5f) * ((float) volumeDims.getZ())) / ((float) volume.Z()));
    }

}
