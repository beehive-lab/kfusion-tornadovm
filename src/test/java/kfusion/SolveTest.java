package kfusion;

import kfusion.algorithms.IterativeClosestPoint;
import tornado.collections.types.Float6;
import tornado.collections.types.FloatOps;
import tornado.collections.types.FloatSE3;
import tornado.collections.types.Matrix4x4Float;



public class SolveTest {

	public static void main(String[] args) {
		//Float6 v = new Float6(0.0004787097877f, 0.001456019769f, -0.001696595198f, 0.001594305595f, 0.0002660461997f, 0.0004686777447f);
		float[] values = new float[]{-0.003683260176f, -0.007639631629f, -0.001128824428f, 0.003903858364f, -0.003509281203f, -0.004992492497f, 3212.803223f, -232.5692749f, -4758.245117f, -5163.686035f, 6703.038086f, -3705.914551f, 5589.393555f, -992.1624756f, -4401.467773f, 1114.671387f, 5879.70752f, 24602.80078f, 27276.56836f, -28787.75781f, 4049.014648f, 32576.00391f, -31777.23047f, 1103.133301f, 35161.12109f, -5802.330078f, 10325.82129f};
		
		
		
		Float6 result = new Float6();
		
		IterativeClosestPoint.solve(result, values, 1);
		System.out.println(result.toString(FloatOps.fmt6e));
		
		Matrix4x4Float delta = new FloatSE3(result).toMatrix4();
		
		System.out.println(delta.toString(FloatOps.fmt4em));
		

	}

}
