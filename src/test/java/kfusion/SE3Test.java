/* 
 * Copyright 2017 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kfusion;

import tornado.collections.types.Float6;
import tornado.collections.types.FloatOps;
import tornado.collections.types.FloatSE3;
import tornado.collections.types.Matrix4x4Float;



public class SE3Test {

	public static void main(String[] args) {
		//Float6 v = new Float6(0.0004787097877f, 0.001456019769f, -0.001696595198f, 0.001594305595f, 0.0002660461997f, 0.0004686777447f);
		
		Float6 v = new Float6(-2.614909282e-06f, 2.224459621e-06f, -6.481725841e-06f, 2.636752009e-06f, -2.938935493e-06f, -2.080130393e-06f);
		System.out.println(v.toString(FloatOps.fmt6e));
		System.out.println();
		Matrix4x4Float m = new FloatSE3(v).toMatrix4();
		System.out.println(m.toString(FloatOps.fmt4em));

	}

}
