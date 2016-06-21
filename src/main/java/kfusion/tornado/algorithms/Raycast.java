package kfusion.tornado.algorithms;

import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.ReadWrite;
import tornado.collections.graphics.GraphicsMath;
import tornado.collections.types.Float3;
import tornado.collections.types.Float4;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.VolumeOps;
import tornado.collections.types.VolumeShort2;
import static tornado.collections.types.Float3.*;

public class Raycast {
	
	private static final float	INVALID	= -2;

	public static final void raycast(@ReadWrite ImageFloat3 verticies, @ReadWrite ImageFloat3 normals, @Read VolumeShort2 volume, @Read Float3 volumeDims, @Read Matrix4x4Float view, @Read float nearPlane, @Read float farPlane,@Read float largeStep,@Read float smallStep){
	
	// use volume model to generate a reference view by raycasting ...
	for (@Parallel int y = 0; y < verticies.Y(); y++) {
		for (@Parallel int x = 0; x < verticies.X(); x++) {
			
//		    final boolean debug = x == 93 && y == 239;
		    
			final Float4 hit = GraphicsMath.raycastPoint(volume,
					volumeDims, x, y, view, nearPlane,
					farPlane, smallStep, largeStep);

//			if(debug){
//			System.out.printf("hit: %s\n",hit.toString());
//			}
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
			normals.set(x,y, normal);

		}
	}
	}
}
