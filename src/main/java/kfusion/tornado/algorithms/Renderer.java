/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package kfusion.tornado.algorithms;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageByte4;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat8;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Byte3;
import uk.ac.manchester.tornado.api.types.vectors.Byte4;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Short3;
import uk.ac.manchester.tornado.api.types.volumes.VolumeShort2;

import static uk.ac.manchester.tornado.api.math.TornadoMath.max;
import static uk.ac.manchester.tornado.api.math.TornadoMath.min;
import static uk.ac.manchester.tornado.api.types.utils.VolumeOps.interp;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.add;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.div;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.mult;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.sub;

public class Renderer {

    private static final float INVALID = -2f;

    // Named to avoid Float3.dot(): reached (via raycastPoint) from renderVolume,
    // where TornadoVM's OpenCL sketcher emits it as a standalone generated
    // function instead of inlining it, and "dot" collides with OpenCL C's
    // builtin dot() function.
    private static float dotProduct(Float3 a, Float3 b) {
        return a.getX() * b.getX() + a.getY() * b.getY() + a.getZ() * b.getZ();
    }

    public static void renderLight(ImageByte4 output, ImageFloat3 verticies, ImageFloat3 normals, Float3 light, Float3 ambient) {
        for (@Parallel int y = 0; y < output.Y(); y++) {
            for (@Parallel int x = 0; x < output.X(); x++) {
                final Float3 normal = normals.get(x, y);
                Byte4 pixel = new Byte4((byte) 0, (byte) 0, (byte) 0, (byte) 255);
                if (normal.getX() != INVALID) {
                    final Float3 vertex = Float3.normalise(Float3.sub(light, verticies.get(x, y)));
                    final float dir = Math.max(Float3.dot(normal, vertex), 0f);
                    Float3 col = add(ambient, dir);
                    col = Float3.clamp(col, 0f, 1f);
                    col = Float3.scale(col, 255f);
                    pixel = new Byte4((byte) col.getX(), (byte) col.getY(), (byte) col.getZ(), (byte) 255);
                }
                output.set(x, y, pixel);
            }
        }
    }

    // GraphicsMath.raycastPoint() and VolumeOps.grad()'s bodies are inlined /
    // reproduced directly here (rather than called as helpers), for the same
    // reason as Raycast.raycast() and GraphicsMath.gradX(): renderVolume() is
    // itself a TornadoVM task entry method, and each of raycastPoint() (~749
    // Graal IR nodes) and grad() (~946 nodes) individually exceeds the default
    // ~600-node per-callee inlining cap - calling them directly from the root
    // (as this method previously did) doesn't exempt them, only the root
    // invocation itself is exempt.
    public static void renderVolume(ImageByte4 output, VolumeShort2 volume, Float3 volumeDims, Matrix4x4Float view, float nearPlane, float farPlane, float smallStep, float largeStep, Float3 light,
            Float3 ambient) {
        for (@Parallel int y = 0; y < output.Y(); y++) {
            for (@Parallel int x = 0; x < output.X(); x++) {

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

                // Hit result as separate scalar floats, not a Float4 - see the
                // comment on the equivalent code in Raycast.raycast() for why
                // (a vector-typed Phi merging a constant zero vector with a
                // computed one trips a TornadoVM Metal backend code-gen bug).
                float hitX = 0f, hitY = 0f, hitZ = 0f, hitW = 0f;
                if (tnear < tfar) {
                    float t = tnear;
                    float stepsize = largeStep;

                    Float3 pos = add(mult(direction, t), origin);
                    float interpValue = interp(volume, volumeDims, pos);
                    float interpChanged = 0f;

                    if (interpValue > 0) {
                        // No `break` - see the equivalent comment in Raycast.raycast(): on
                        // TornadoVM's jdk25 Metal/OpenCL backends, a value conditionally
                        // reassigned inside a loop via break and then read after the loop
                        // isn't reliably propagated. A boolean folded into the loop condition
                        // gives the loop a single exit edge instead of two (natural + break)
                        // merging downstream, which sidesteps the bug entirely.
                        boolean crossed = false;
                        while (t < tfar && !crossed) {
                            pos = add(mult(direction, t), origin);
                            interpChanged = interp(volume, volumeDims, pos);

                            if (interpChanged < 0f) {
                                crossed = true;
                            } else {
                                if (interpChanged < 0.8f) {
                                    stepsize = smallStep;
                                }

                                interpValue = interpChanged;
                                t += stepsize;
                            }
                        }

                        if (crossed) {
                            t = t + ((stepsize * interpChanged) / (interpValue - interpChanged));
                            pos = add(mult(direction, t), origin);
                            hitX = pos.getX();
                            hitY = pos.getY();
                            hitZ = pos.getZ();
                            hitW = t;
                        }
                    }
                }

                // pixel is kept as scalar bytes (not Byte4) until the final
                // .set() call, for the same reason as hitX/Y/Z/W and
                // posX/Y/Z/normX/Y/Z in Raycast.raycast(): a TornadoVM Metal
                // backend bug crashes when a value stored into a vector array
                // element could be a compile-time constant, which new Byte4() is.
                final byte pixelR, pixelG, pixelB, pixelA;
                if (hitW > 0) {
                    final Float3 test = new Float3(hitX, hitY, hitZ);
                    final float gx = GraphicsMath.gradX(volume, volumeDims, test);
                    final float gy = GraphicsMath.gradY(volume, volumeDims, test);
                    final float gz = GraphicsMath.gradZ(volume, volumeDims, test);
                    final Float3 surfNorm = mult(new Float3(gx, gy, gz), GraphicsMath.gradScale(volume, volumeDims));
                    if (Float3.length(surfNorm) > 0) {
                        final Float3 diff = Float3.normalise(Float3.sub(light, test));
                        final Float3 normalizedSurfNorm = Float3.normalise(surfNorm);
                        final float dir = Math.max(dotProduct(normalizedSurfNorm, diff), 0f);
                        Float3 col = add(new Float3(dir, dir, dir), ambient);
                        col = Float3.clamp(col, 0f, 1f);
                        col = Float3.mult(col, 255f);
                        pixelR = (byte) col.getX();
                        pixelG = (byte) col.getY();
                        pixelB = (byte) col.getZ();
                        pixelA = (byte) 0;
                    } else {
                        pixelR = 0;
                        pixelG = 0;
                        pixelB = 0;
                        pixelA = 0;
                    }
                } else {
                    pixelR = 0;
                    pixelG = 0;
                    pixelB = 0;
                    pixelA = 0;
                }
                output.set(x, y, new Byte4(pixelR, pixelG, pixelB, pixelA));
            }
        }
    }

    public static void renderNorms(ImageByte3 output, ImageFloat3 normals) {
        for (@Parallel int y = 0; y < normals.Y(); y++) {
            for (@Parallel int x = 0; x < normals.X(); x++) {
                final Float3 normal = normals.get(x, y);
                final Byte3 pixel = new Byte3();
                if (normal.getX() != INVALID) {
                    Float3.normalise(normal);
                    mult(normal, 128f);
                    add(normal, 128f);
                    pixel.setX((byte) normal.getX());
                    pixel.setY((byte) normal.getY());
                    pixel.setZ((byte) normal.getZ());
                }
                output.set(x, y, pixel);
            }
        }
    }

    public static void renderVertex(ImageByte3 output, ImageFloat3 vertices) {
        for (@Parallel int y = 0; y < vertices.Y(); y++) {
            for (@Parallel int x = 0; x < vertices.X(); x++) {
                final Float3 vertex = vertices.get(x, y);
                final Byte3 pixel = new Byte3();
                if (vertex.getZ() != 0) {
                    Float3.normalise(vertex);
                    mult(vertex, 128f);
                    add(vertex, 128f);
                    pixel.setX((byte) vertex.getZ());
                    pixel.setY((byte) 0);
                    pixel.setZ((byte) 0);
                }
                output.set(x, y, pixel);
            }
        }
    }

    public static void renderTrack(ImageByte3 output, ImageFloat8 track) {
        for (@Parallel int y = 0; y < track.Y(); y++) {
            for (@Parallel int x = 0; x < track.X(); x++) {
                Byte3 pixel = null;
                final int result = (int) track.get(x, y).getS7();
                switch (result) {
                    case 1: // ok GREY
                        pixel = new Byte3((byte) 128, (byte) 128, (byte) 128);
                        break;
                    case -1: // no input BLACK
                        pixel = new Byte3((byte) 0, (byte) 0, (byte) 0);
                        break;
                    case -2: // not in image RED
                        pixel = new Byte3((byte) 255, (byte) 0, (byte) 0);
                        break;
                    case -3: // no correspondence GREEN
                        pixel = new Byte3((byte) 0, (byte) 255, (byte) 0);
                        break;
                    case -4: // too far away BLUE
                        pixel = new Byte3((byte) 0, (byte) 0, (byte) 255);
                        break;
                    case -5: // wrong normal YELLOW
                        pixel = new Byte3((byte) 255, (byte) 255, (byte) 0);
                        break;
                    default:
                        pixel = new Byte3((byte) 255, (byte) 128, (byte) 128);
                        break;
                }
                output.set(x, y, pixel);
            }
        }
    }

    public static void renderDepth(ImageByte4 output, ImageFloat depthMap, float nearPlane, float farPlane) {
        final Byte4 blackPixel = new Byte4((byte) 255, (byte) 255, (byte) 255, (byte) 0);
        final Byte4 zero = new Byte4();
        for (@Parallel int y = 0; y < depthMap.Y(); y++) {
            for (@Parallel int x = 0; x < depthMap.X(); x++) {
                float depth = depthMap.get(x, y);
                Byte4 pixel = null;
                if (depth < nearPlane) {
                    pixel = blackPixel; // black
                } else if (depth >= farPlane) {
                    pixel = zero;
                } else {
                    final float h = ((depth - nearPlane) / (farPlane - nearPlane)) * 6f;
                    final int sextant = (int) h;
                    final float fract = h - sextant;
                    final float mid1 = 0.25f + (0.5f * fract);
                    final float mid2 = 0.75f - (0.5f * fract);
                    switch (sextant) {
                        case 0:
                            pixel = new Byte4((byte) 191, (byte) (255f * mid1), (byte) 64, (byte) 0);
                            break;
                        case 1:
                            pixel = new Byte4((byte) (255f * mid2), (byte) 191, (byte) 64, (byte) 0);
                            break;
                        case 2:
                            pixel = new Byte4((byte) 64, (byte) 191, (byte) (255f * mid1), (byte) 0);
                            break;
                        case 3:
                            pixel = new Byte4((byte) 64, (byte) (255f * mid2), (byte) 191, (byte) 0);
                            break;
                        case 4:
                            pixel = new Byte4((byte) (255f * mid1), (byte) 64, (byte) 191, (byte) 0);
                            break;
                        case 5:
                            pixel = new Byte4((byte) 191, (byte) 64, (byte) (255f * mid2), (byte) 0);
                            break;
                        default:
                            pixel = zero;
                    }
                }
                output.set(x, y, pixel);
            }
        }
    }

    public static void gs2rgb(Short3 rgb, float d) {
        final float v = 0.75f;
        float r = 0;
        float g = 0;
        float b = 0;
        float m = 0.25f;
        float sv = 0.6667f;
        int sextant;
        float fract;
        float vsf;
        float mid1;
        float mid2;
        d *= 6.0;
        sextant = (int) d;
        fract = d - sextant;
        vsf = v * sv * fract;
        mid1 = m + vsf;
        mid2 = v - vsf;
        switch (sextant) {
            case 0:
                r = v;
                g = mid1;
                b = m;
                break;
            case 1:
                r = mid2;
                g = v;
                b = m;
                break;
            case 2:
                r = m;
                g = v;
                b = mid1;
                break;
            case 3:
                r = m;
                g = mid2;
                b = v;
                break;
            case 4:
                r = mid1;
                g = m;
                b = v;
                break;
            case 5:
                r = v;
                g = m;
                b = mid2;
                break;
            default:
                break;
        }
        rgb.setX((short) (r * 255));
        rgb.setY((short) (g * 255));
        rgb.setZ((short) (b * 255));
    }

    public static void renderRGB(ImageByte3 output, ImageByte3 video) {
        for (@Parallel int y = 0; y < video.Y(); y++) {
            for (@Parallel int x = 0; x < video.X(); x++) {
                output.set(x, y, video.get(x, y));
            }
        }
    }
}
