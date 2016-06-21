package kfusion.tornado.algorithms;

import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.ReadWrite;
import tornado.collections.math.TornadoMath;
import static tornado.collections.math.TornadoMath.*;
import static tornado.collections.graphics.GraphicsMath.*;
import static tornado.collections.types.Float2.*;
import static tornado.collections.types.Float3.*;
import tornado.collections.types.Float2;
import tornado.collections.types.Float3;
import tornado.collections.types.FloatOps;
import tornado.collections.types.ImageFloat;
import tornado.collections.types.Int2;
import tornado.collections.types.Int3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.Short2;
import tornado.collections.types.VolumeShort2;

public class Integration {
	
	public static void integrate(@Read ImageFloat filteredDepthImage,@Read  Matrix4x4Float invTrack,@Read  Matrix4x4Float K, @Read Float3 volumeDims, @ReadWrite VolumeShort2 volume, @Read float mu, @Read float maxWeight){
		final Float3 tmp = new Float3(0f, 0f, volumeDims.getZ() / (float) volume.Z());
	
		final Float3 integrateDelta = rotate(invTrack, tmp);
		final Float3 cameraDelta = rotate(K, integrateDelta);

		for (@Parallel int y = 0; y < volume.Y(); y++) {
			for (@Parallel int x = 0; x < volume.X(); x++) {

				final Int3 pix = new Int3(x, y, 0);
				Float3 pos = rigidTransform(invTrack, pos(volume, volumeDims, pix));
				Float3 cameraX = rigidTransform(K, pos);
				
				for (int z = 0; z < volume.Z(); z++,
					pos = add(pos,integrateDelta),
					cameraX = add(cameraX, cameraDelta)){
				

					if (pos.getZ() < 0.0001f) // arbitrary near plane constant
						continue;

					final Float2 pixel = new Float2(
							(cameraX.getX() / cameraX.getZ()) + 0.5f,
							(cameraX.getY() / cameraX.getZ()) + 0.5f );

					
					if ((pixel.getX() < 0) 
							|| (pixel.getX() > (filteredDepthImage.X() - 1))
							|| (pixel.getY() < 0)
							|| (pixel.getY() > (filteredDepthImage.Y() - 1))) continue;

					final Int2 px = new Int2( (int) pixel.getX(),
							(int) pixel.getY());

					final float depth = filteredDepthImage.get(px.getX(), px.getY());
					
					if (depth == 0) continue;

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

						final Float2 floatValue = mult(new Float2(dx,dy),constantValue2);
						final Short2 outputValue = new Short2((short) floatValue.getX(), (short) floatValue.getY());
						
						volume.set(x,y,z,outputValue);
					}
				}
			}
		}
	}
	
	final private static Float3 pos(final VolumeShort2 volume, final Float3 volumeDims, final Int3 p) {
		return new Float3(
				((((float) p.getX()) + 0.5f) * ((float) volumeDims.getX()) ) / ((float) volume.X()),
				((((float) p.getY()) + 0.5f) * ((float) volumeDims.getY()) ) / ((float) volume.Y()),
				((((float) p.getZ()) + 0.5f) * ((float) volumeDims.getZ()) ) / ((float) volume.Z()));
	}
	
}
