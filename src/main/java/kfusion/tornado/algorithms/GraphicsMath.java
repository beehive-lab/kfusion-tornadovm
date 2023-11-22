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
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.volumes.VolumeShort2;

import static uk.ac.manchester.tornado.api.math.TornadoMath.max;
import static uk.ac.manchester.tornado.api.math.TornadoMath.min;
import static uk.ac.manchester.tornado.api.types.utils.VolumeOps.interp;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.add;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.cross;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.div;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.dot;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.mult;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.normalise;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.sub;

public class GraphicsMath {

    private static final float INVALID = -2;

    public static void vertex2normal(ImageFloat3 normals, ImageFloat3 verticies) {
        for (@Parallel int y = 0; y < normals.Y(); y++) {
            for (@Parallel int x = 0; x < normals.X(); x++) {
                final Float3 left = verticies.get(Math.max(x - 1, 0), y);
                final Float3 right = verticies.get(Math.min(x + 1, verticies.X() - 1), y);
                final Float3 up = verticies.get(x, Math.max(y - 1, 0));
                final Float3 down = verticies.get(x, Math.min(y + 1, verticies.Y() - 1));

                final Float3 dxv = sub(right, left);
                final Float3 dyv = sub(down, up);

                boolean invalidNormal = left.getZ() == 0 || right.getZ() == 0 || up.getZ() == 0 || down.getZ() == 0;
                final Float3 normal;
                if (invalidNormal) {
                    normal = new Float3(INVALID, 0f, 0f);
                } else {
                    normal = normalise(cross(dyv, dxv));
                }
                normals.set(x, y, normal);
            }
        }
    }

    public static void depth2vertex(ImageFloat3 vertices, ImageFloat depths, Matrix4x4Float invK) {
        for (@Parallel int y = 0; y < depths.Y(); y++) {
            for (@Parallel int x = 0; x < depths.X(); x++) {
                final float depth = depths.get(x, y);
                final Float3 pix = new Float3(x, y, 1f);
                final Float3 vertex = (depth > 0) ? mult(rotate(invK, pix), depth) : new Float3();
                vertices.set(x, y, vertex);
            }
        }
    }

    public static Float3 rotate(Matrix4x4Float m, Float3 x) {
        return new Float3(dot(m.row(0).asFloat3(), x), dot(m.row(1).asFloat3(), x), dot(m.row(2).asFloat3(), x));
    }

    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public static void getInverseCameraMatrix(Float4 k, Matrix4x4Float m) {
        m.fill(0f);
        m.set(0, 0, 1f / k.getX());
        m.set(0, 2, -k.getZ() / k.getX());
        m.set(1, 1, 1f / k.getY());
        m.set(1, 2, -k.getW() / k.getY());
        m.set(2, 2, 1);
        m.set(3, 3, 1);
    }

    /**
     * * Creates a 4x4 matrix representing the intrinsic camera matrix.
     *
     * @param k
     *     - camera parameters {f_x,f_y,x_0,y_0} where {f_x,f_y} specifies
     *     the focal length of the camera and {x_0,y_0} the principle point
     * @param m
     *     - returned matrix
     */
    public static void getCameraMatrix(Float4 k, Matrix4x4Float m) {
        m.fill(0f);

        // focal length - f_x
        m.set(0, 0, k.getX());
        // focal length - f_y
        m.set(1, 1, k.getY());

        // principle point - x_0
        m.set(0, 2, k.getZ());

        // principle point - y_0
        m.set(1, 2, k.getW());

        m.set(2, 2, 1);
        m.set(3, 3, 1);
    }

    /*
     * Performs a rigid transformation which maps one co-ordinate system to another
     * [ R11 R12 R13 t1 ] R => 3x3 rotation matrix T = [ R21 R22 R23 t2 ] t => 3x1
     * translation (column vector) [ R31 R32 R33 t3 ] [ 0 0 0 1 ] P = [ x ] column
     * vector representing the point to be transformed [ y ] [ z ] [ 1 ]
     */
    public static Float3 rigidTransform(Matrix4x4Float matrix, Float3 point) {
        final Float3 translation = matrix.column(3).asFloat3();
        final Float3 rotation = new Float3(dot(matrix.row(0).asFloat3(), point), dot(matrix.row(1).asFloat3(), point), dot(matrix.row(2).asFloat3(), point));
        return add(rotation, translation);
    }

    public static Float4 raycastPoint(final VolumeShort2 volume, final Float3 dim, final int x, final int y, final Matrix4x4Float view, float nearPlane, float farPlane, float smallStep,
            float largeStep) {

        final Float3 position = new Float3(x, y, 1f);

        // retrive translation from matrix (col 3, elements =3 )
        final Float3 origin = view.column(3).asFloat3();

        final Float3 direction = rotate(view, position);
        final Float3 invR = div(new Float3(1f, 1f, 1f), direction);

        final Float3 tbot = mult(mult(invR, origin), -1f);
        final Float3 ttop = mult(invR, sub(dim, origin));

        final Float3 tmin = Float3.min(ttop, tbot);
        final Float3 tmax = Float3.max(ttop, tbot);

        final float largestTmin = Float3.max(tmin);
        final float smallestTmax = Float3.min(tmax);

        final float tnear = max(largestTmin, nearPlane);
        final float tfar = min(smallestTmax, farPlane);

        if (tnear < tfar) {

            float t = tnear;
            float stepsize = largeStep;

            Float3 pos = add(mult(direction, t), origin);

            float interp = interp(volume, dim, pos);

            float interpChanged = 0f;
            if (interp > 0) {
                for (; t < tfar; t += stepsize) {
                    pos = add(mult(direction, t), origin);

                    interpChanged = interp(volume, dim, pos);

                    if (interpChanged < 0f) {
                        break;
                    }

                    if (interpChanged < 0.8f) {
                        stepsize = smallStep;
                    }

                    interp = interpChanged;
                }

                if (interpChanged < 0) {
                    t = t + ((stepsize * interpChanged) / (interp - interpChanged));
                    pos = add(mult(direction, t), origin);
                    return new Float4(pos.getX(), pos.getY(), pos.getZ(), t);
                }
            }
        }
        return new Float4();
    }
}
