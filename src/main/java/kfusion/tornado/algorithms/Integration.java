/*
 *    This file is part of Slambench-Tornado: A Tornado version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-tornado
 *
 *    Copyright (c) 2013-2017 APT Group, School of Computer Science,
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
package kfusion.tornado.algorithms;

import static uk.ac.manchester.tornado.collections.graphics.GraphicsMath.rigidTransform;
import static uk.ac.manchester.tornado.collections.graphics.GraphicsMath.rotate;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.min;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.sqrt;
import static uk.ac.manchester.tornado.collections.types.Float2.mult;
import static uk.ac.manchester.tornado.collections.types.Float3.add;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.collections.math.TornadoMath;
import uk.ac.manchester.tornado.collections.types.Float2;
import uk.ac.manchester.tornado.collections.types.Float3;
import uk.ac.manchester.tornado.collections.types.FloatOps;
import uk.ac.manchester.tornado.collections.types.ImageFloat;
import uk.ac.manchester.tornado.collections.types.Int2;
import uk.ac.manchester.tornado.collections.types.Int3;
import uk.ac.manchester.tornado.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.collections.types.Short2;
import uk.ac.manchester.tornado.collections.types.VolumeShort2;

public class Integration {

	public static void integrate(ImageFloat filteredDepthImage, Matrix4x4Float invTrack, Matrix4x4Float K,
			Float3 volumeDims, VolumeShort2 volume, float mu, float maxWeight) {
		final Float3 tmp = new Float3(0f, 0f, volumeDims.getZ() / volume.Z());

		final Float3 integrateDelta = rotate(invTrack, tmp);
		final Float3 cameraDelta = rotate(K, integrateDelta);

		for (@Parallel int y = 0; y < volume.Y(); y++) {
			for (@Parallel int x = 0; x < volume.X(); x++) {

				final Int3 pix = new Int3(x, y, 0);
				Float3 pos = rigidTransform(invTrack, pos(volume, volumeDims, pix));
				Float3 cameraX = rigidTransform(K, pos);

				for (int z = 0; z < volume.Z(); z++, pos = add(pos, integrateDelta), cameraX = add(cameraX,
						cameraDelta)) {

					if (pos.getZ() < 0.0001f) // arbitrary near plane constant
					{
						continue;
					}

					final Float2 pixel = new Float2((cameraX.getX() / cameraX.getZ()) + 0.5f,
							(cameraX.getY() / cameraX.getZ()) + 0.5f);

					if ((pixel.getX() < 0) || (pixel.getX() > (filteredDepthImage.X() - 1)) || (pixel.getY() < 0)
							|| (pixel.getY() > (filteredDepthImage.Y() - 1))) {
						continue;
					}

					final Int2 px = new Int2((int) pixel.getX(), (int) pixel.getY());

					final float depth = filteredDepthImage.get(px.getX(), px.getY());

					if (depth == 0) {
						continue;
					}

					final float diff = (depth - cameraX.getZ())
							* sqrt(1f + FloatOps.sq(pos.getX() / pos.getZ()) + FloatOps.sq(pos.getY() / pos.getZ()));

					if (diff > -mu) {

						final float sdf = min(1f, diff / mu);

						final Short2 inputValue = volume.get(x, y, z);
						final Float2 constantValue1 = new Float2(0.00003051944088f, 1f);
						final Float2 constantValue2 = new Float2(32766.0f, 1f);

						final Float2 data = mult(new Float2(inputValue.getX(), inputValue.getY()), constantValue1);

						final float dx = TornadoMath.clamp(((data.getY() * data.getX()) + sdf) / (data.getY() + 1f),
								-1f, 1f);
						final float dy = min(data.getY() + 1f, maxWeight);

						final Float2 floatValue = mult(new Float2(dx, dy), constantValue2);
						final Short2 outputValue = new Short2((short) floatValue.getX(), (short) floatValue.getY());

						volume.set(x, y, z, outputValue);
					}
				}
			}
		}
	}

	private static Float3 pos(final VolumeShort2 volume, final Float3 volumeDims, final Int3 p) {
		return new Float3(((p.getX() + 0.5f) * volumeDims.getX()) / volume.X(),
				((p.getY() + 0.5f) * volumeDims.getY()) / volume.Y(),
				((p.getZ() + 0.5f) * volumeDims.getZ()) / volume.Z());
	}

}
