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
package kfusion.tornado.algorithms;

import static uk.ac.manchester.tornado.api.math.TornadoMath.max;
import static uk.ac.manchester.tornado.api.math.TornadoMath.min;
import static uk.ac.manchester.tornado.api.types.utils.VolumeOps.interp;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.add;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.div;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.length;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.mult;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.normalise;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.sub;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.volumes.VolumeShort2;

public class Raycast {

	private static final float INVALID = -2;

	// GraphicsMath.raycastPoint()'s body is inlined directly here (rather than
	// called as a helper) because raycast() is the actual TornadoVM task entry
	// method: TornadoVM's inlining-cap check only exempts the root invocation
	// itself, and raycastPoint() alone (once its own nested calls - rotate(),
	// interp(), etc. - are resolved) comes to ~749 Graal IR nodes, over the
	// default ~600-node per-callee cap. Its individual nested calls stay well
	// under that cap on their own, checked independently, once called directly
	// from here with no wrapping method in between (the same pattern applied to
	// mapReduce()/accumulateJtJ in IterativeClosestPoint.java).
	public static final void raycast(ImageFloat3 verticies, ImageFloat3 normals, VolumeShort2 volume, Float3 volumeDims,
			Matrix4x4Float view, float nearPlane, float farPlane, float largeStep, float smallStep) {

		// use volume model to generate a reference view by raycasting ...
		for (@Parallel int y = 0; y < verticies.Y(); y++) {
			for (@Parallel int x = 0; x < verticies.X(); x++) {

				final Float3 pixelPos = new Float3(x, y, 1f);
				final Float3 origin = view.column(3).asFloat3();
				final Float3 direction = GraphicsMath.rotate(view, pixelPos);
				final Float3 invR = div(new Float3(1f, 1f, 1f), direction);

				final Float3 tbot = mult(mult(invR, origin), -1f);
				final Float3 ttop = mult(invR, sub(volumeDims, origin));

				final Float3 tminVec = Float3.min(ttop, tbot);
				final Float3 tmaxVec = Float3.max(ttop, tbot);

				final float largestTmin = Float3.max(tminVec);
				final float smallestTmax = Float3.min(tmaxVec);

				final float tnear = max(largestTmin, nearPlane);
				final float tfar = min(smallestTmax, farPlane);

				// Hit result as separate scalar floats, not a Float4: merging a
				// constant zero vector (the "no hit" case) with a computed one
				// across this branch - unlike the original raycastPoint(), which
				// returned each case from a separate return statement - produces
				// a vector-typed Phi that triggers a TornadoVM Metal backend bug
				// (MetalAssembler.getAbsoluteIndexFromValue: StringIndexOutOfBoundsException
				// on a constant value's format string during VectorStoreStmt
				// code-gen). Scalar float Phis avoid that code path entirely.
				float hitX = 0f, hitY = 0f, hitZ = 0f, hitW = 0f;
				if (tnear < tfar) {
					float t = tnear;
					float stepsize = largeStep;

					Float3 pos = add(mult(direction, t), origin);
					float interpValue = interp(volume, volumeDims, pos);
					float interpChanged = 0f;

					if (interpValue > 0) {
						for (; t < tfar; t += stepsize) {
							pos = add(mult(direction, t), origin);
							interpChanged = interp(volume, volumeDims, pos);

							if (interpChanged < 0f) {
								break;
							}

							if (interpChanged < 0.8f) {
								stepsize = smallStep;
							}

							interpValue = interpChanged;
						}

						if (interpChanged < 0) {
							t = t + ((stepsize * interpChanged) / (interpValue - interpChanged));
							pos = add(mult(direction, t), origin);
							hitX = pos.getX();
							hitY = pos.getY();
							hitZ = pos.getZ();
							hitW = t;
						}
					}
				}

				// position/normal are kept as scalar floats (not Float3) until the
				// final .set() calls below, for the same reason as hitX/Y/Z/W
				// above: a TornadoVM Metal backend bug (emitValueWithFormat(),
				// used for VectorStoreStmt, is missing the ConstantValue handling
				// branch that its sibling toString(Value) has) crashes when the
				// value stored into a vector array element could be a compile-time
				// constant - which new Float3()/new Float3(INVALID, 0f, 0f) are.
				// Scalar Phis avoid ever materializing a constant Float3/Byte4
				// that reaches a .set() call.
				final float posX, posY, posZ;
				final float normX, normY, normZ;
				if (hitW > 0f) {
					posX = hitX;
					posY = hitY;
					posZ = hitZ;

					final Float3 position = new Float3(hitX, hitY, hitZ);

					// VolumeOps.grad(), called directly, is too large a single unit under
					// the default inlining policy - see the comment on GraphicsMath.gradX().
					final float gx = GraphicsMath.gradX(volume, volumeDims, position);
					final float gy = GraphicsMath.gradY(volume, volumeDims, position);
					final float gz = GraphicsMath.gradZ(volume, volumeDims, position);
					final Float3 surfNorm = mult(new Float3(gx, gy, gz), GraphicsMath.gradScale(volume, volumeDims));

					if (length(surfNorm) != 0) {
						final Float3 normal = normalise(surfNorm);
						normX = normal.getX();
						normY = normal.getY();
						normZ = normal.getZ();
					} else {
						normX = INVALID;
						normY = 0f;
						normZ = 0f;
					}
				} else {
					normX = INVALID;
					normY = 0f;
					normZ = 0f;
					posX = 0f;
					posY = 0f;
					posZ = 0f;
				}

				verticies.set(x, y, new Float3(posX, posY, posZ));
				normals.set(x, y, new Float3(normX, normY, normZ));

			}
		}
	}
}
