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

public class OverflowTest {

	public static void main(String[] args) {
		
		
		byte[] b = new byte[2];
		b[0] = Byte.parseByte(args[0]);
		b[1] = Byte.parseByte(args[1]);
		
		// b[1] << 8
		// b[0] & 0xf0 >>> 4
		// b[1] & 0xf
		byte tmp = (byte) ((b[0] & 0xf0) >>> 4);
		
		System.out.printf("bits [8-11]: %s\n", Integer.toBinaryString(b[1]&0x7));
		System.out.printf("bits [5-7] : %s\n", Integer.toBinaryString(tmp));
		System.out.printf("bits [0-4] : %s\n", Integer.toBinaryString(b[0] & 0xf));
		
		short s = (short) ((b[1] & 0x7 )<< 8);
		s |= tmp << 4;
		s |= b[0] & 0xf;
		
		float f = (float) s;
		
		System.out.printf("bytes = %d %d [%s %s]\n", b[0],b[1],Integer.toBinaryString(b[0]),Integer.toBinaryString(b[1]));
		System.out.printf("short = %d [%16s]\n", s,Integer.toBinaryString(s));
		System.out.printf("float = %f\n", f);

	}

}
