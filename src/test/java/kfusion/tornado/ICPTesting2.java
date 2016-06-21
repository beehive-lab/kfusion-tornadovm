package kfusion.tornado;

import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import kfusion.TornadoModel;
import kfusion.algorithms.IterativeClosestPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import tornado.collections.types.Float8;
import tornado.collections.types.FloatOps;
import tornado.collections.types.ImageFloat8;
import tornado.common.exceptions.TornadoRuntimeException;
import tornado.runtime.api.TaskGraph;
import static kfusion.algorithms.IterativeClosestPoint.*;

public class ICPTesting2 {
    private final TornadoModel config = new TornadoModel();
	public static void loadTrackData(final String file, final ImageFloat8 dest) throws Exception {
		final FileInputStream fis = new FileInputStream(file);
		final FileChannel vChannel = fis.getChannel();

		final ByteBuffer bb = ByteBuffer.allocate((int) vChannel.size());
		bb.order(ByteOrder.LITTLE_ENDIAN);

		vChannel.read(bb);
		bb.flip();

		vChannel.close();

		int i = 0;
		while (bb.hasRemaining()) {
			final int x = i % dest.X();
			final int y = i / dest.X();
			dest.set(x, y, slurpTrackData(bb));

			i++;
		}

		fis.close();

	}

	public static void main(final String[] args) {
		final ICPTesting2 testing = new ICPTesting2();

		try {
			testing.setUp();

			testing.testTrack();
			testing.tearDown();

		} catch (final Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static Float8 slurpTrackData(final ByteBuffer buffer) {
		final Float8 result = new Float8();
		result.setS7(buffer.getInt());
		result.setS6(buffer.getFloat());
		for (int i = 0; i < 6; i++) {
			result.set(i, buffer.getFloat());
		}
		return result;
	}

	private float							dist_threshold;

	final private String					FILE_PATH		= "/Users/jamesclarkson/Downloads/kfusion_ut_data";
	final int								height			= 240;
	final int								width			= 320;

	private ImageFloat8		input;
	private float[] output;

	final String							track_prefix	= "track_";

	private TaskGraph						graph;




	private boolean compareTrackData(final Float8 value, final Float8 ref) {
		boolean output = true;
		final int result = (int) value.getS7();

		if (!FloatOps.compareBits(value.getS7(), ref.getS7())) {
			output = false;
		} else if (result > 0) { // only get errors on negative results
			if (!FloatOps.compareBits(value.getS6(), ref.getS6())) {
				output = false;
			} else {
				for (int i = 0; i < 6; i++) {
					if (!FloatOps.compareBits(value.get(i), ref.get(i))) {
						output = false;
						break;
					}
				}
			}
		}
		return output;
	}

	@Before
	public void setUp() throws Exception {

		input = new ImageFloat8(width, height);
		output = new float[32];

		final float[] intermediate = new float[8192];

		//@formatter:off
		graph = new TaskGraph()
		    .streamIn(input)
			.add(IterativeClosestPoint::reduce1,intermediate, input)
			.add(IterativeClosestPoint::reduce2,output, intermediate)
			.streamOut(output)
			.mapAllTo(config.getTornadoDevice());
		//@formatter:on
	}

	@After
	public void tearDown() throws Exception {

	}

	@Test
	public void testTrack() throws TornadoRuntimeException {

		final int[] frames = { 2, 13, 12, 14, 16, 15, 27, 26, 28, 30, 29, 40, 42, 41, 43, 54, 53,
				55, 57, 56, 58, 60, 59, 62, 61 };
		// final int[] frames = {0,3,4,5,6,17,18,19,20,31,32,33,34,44,45,46,47};

		int badFrames = 0;

		for (int j = 1; j < 2; j++) {

			final int i = frames[j];
			try {
				loadTrackData(String.format("%s/%soutput.out.%04d", FILE_PATH, track_prefix, i),
						input);

			} catch (final Exception e) {
				fail("Unable to load data: " + e.getMessage());
			}

				final float[] ref = new float[32];
				final long start = System.nanoTime();
				reduce(ref, input);
				final long end = System.nanoTime();
				System.out.printf("track: %f s\n", (end - start) * 1e-9f);
			
				
				graph.schedule().waitOn();
				graph.dumpTimes();
			
				int errors = 0;
				for(int x=0;x<32;x++){
					if(!FloatOps.compareBits(output[x], ref[x]))
						errors++;
				}

				if(errors > 0)
					badFrames++;
				
				System.out.printf("[%d]: errors %d\n",j,errors);

		}

		if (badFrames > 0) {
			final double badFramesPct = ((double) badFrames / (double) frames.length) * 100;
			fail(String.format("Errors found on %d frames (%.2f %%)", badFrames, badFramesPct));
		}
	}

}
