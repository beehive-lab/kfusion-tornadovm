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
