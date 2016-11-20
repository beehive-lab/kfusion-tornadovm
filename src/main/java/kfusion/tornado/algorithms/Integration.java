package kfusion.tornado.algorithms;

import tornado.api.Parallel;
import tornado.collections.math.TornadoMath;
import tornado.collections.types.*;

import static tornado.collections.graphics.GraphicsMath.rigidTransform;
import static tornado.collections.graphics.GraphicsMath.rotate;
import static tornado.collections.math.TornadoMath.min;
import static tornado.collections.math.TornadoMath.sqrt;
import static tornado.collections.types.Float2.mult;
import static tornado.collections.types.Float3.add;

public class Integration {

    public static void integrate(ImageFloat filteredDepthImage, Matrix4x4Float invTrack, Matrix4x4Float K, Float3 volumeDims, VolumeShort2 volume, float mu, float maxWeight) {
        final Float3 tmp = new Float3(0f, 0f, volumeDims.getZ() / volume.Z());

        final Float3 integrateDelta = rotate(invTrack, tmp);
        final Float3 cameraDelta = rotate(K, integrateDelta);

        for (@Parallel int y = 0; y < volume.Y(); y++) {
            for (@Parallel int x = 0; x < volume.X(); x++) {

                final Int3 pix = new Int3(x, y, 0);
                Float3 pos = rigidTransform(invTrack, pos(volume, volumeDims, pix));
                Float3 cameraX = rigidTransform(K, pos);

                for (int z = 0; z < volume.Z(); z++, pos = add(pos, integrateDelta), cameraX = add(cameraX, cameraDelta)) {

                    if (pos.getZ() < 0.0001f) // arbitrary near plane constant
                    {
                        continue;
                    }

                    final Float2 pixel = new Float2(
                            (cameraX.getX() / cameraX.getZ()) + 0.5f,
                            (cameraX.getY() / cameraX.getZ()) + 0.5f);

                    if ((pixel.getX() < 0)
                            || (pixel.getX() > (filteredDepthImage.X() - 1))
                            || (pixel.getY() < 0)
                            || (pixel.getY() > (filteredDepthImage.Y() - 1))) {
                        continue;
                    }

                    final Int2 px = new Int2((int) pixel.getX(),
                            (int) pixel.getY());

                    final float depth = filteredDepthImage.get(px.getX(), px.getY());

                    if (depth == 0) {
                        continue;
                    }

                    final float diff = (depth - cameraX.getZ())
                            * sqrt(1f
                                    + FloatOps.sq(pos.getX() / pos.getZ())
                                    + FloatOps.sq(pos.getY() / pos.getZ()));

                    if (diff > -mu) {

                        final float sdf = min(1f, diff / mu);

                        final Short2 inputValue = volume.get(x, y, z);
                        final Float2 constantValue1 = new Float2(0.00003051944088f, 1f);
                        final Float2 constantValue2 = new Float2(32766.0f, 1f);

                        final Float2 data = mult(new Float2(inputValue.getX(), inputValue.getY()), constantValue1);

                        final float dx = TornadoMath.clamp(((data.getY() * data.getX()) + sdf) / (data.getY() + 1f), -1f, 1f);
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
        return new Float3(
                ((p.getX() + 0.5f) * volumeDims.getX()) / volume.X(),
                ((p.getY() + 0.5f) * volumeDims.getY()) / volume.Y(),
                ((p.getZ() + 0.5f) * volumeDims.getZ()) / volume.Z());
    }

}
