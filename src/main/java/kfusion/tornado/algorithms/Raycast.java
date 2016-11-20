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
