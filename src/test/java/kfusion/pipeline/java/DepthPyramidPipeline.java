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
package kfusion.pipeline.java;

import kfusion.KfusionConfig;
import kfusion.Utils;
import kfusion.devices.TestingDevice;
import kfusion.pipeline.AbstractPipeline;
import tornado.collections.graphics.GraphicsMath;
import tornado.collections.types.Float3;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;

public class DepthPyramidPipeline extends AbstractPipeline<KfusionConfig> {

	public DepthPyramidPipeline(KfusionConfig config) {
        super(config);
    }

    private Matrix4x4Float[]	invK;
	private ImageFloat3[]		refVerticies;
	private ImageFloat3[]		refNormals;

	private static String makeFilename(String path, int frame, String kernel, String variable,
			boolean isInput) {
		return String.format("%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in"
				: "out");
	}

	private void loadFrame(String path, int index) {
		invK = new Matrix4x4Float[3];
		refVerticies = new ImageFloat3[3];
		refNormals = new ImageFloat3[3];

		try {

			for (int i = 0; i < 3; i++) {
				invK[i] = new Matrix4x4Float();
				refVerticies[i] = pyramidVerticies[i].duplicate();
				refNormals[i] = pyramidNormals[i].duplicate();

				Utils.loadData(makeFilename(path, index, "depth2vertex_" + i, "depths", true),
						pyramidDepths[i].asBuffer());
				Utils.loadData(makeFilename(path, index, "depth2vertex_" + i, "invK", true),
						invK[i].asBuffer());

				Utils.loadData(makeFilename(path, index, "depth2vertex_" + i, "verticies", false),
						refVerticies[i].asBuffer());
				Utils.loadData(makeFilename(path, index, "vertex2normal_" + i, "normals", false),
						refNormals[i].asBuffer());

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean validate(String path, int index) {
		boolean valid = true;
		for (int i = 0; i < 3; i++) {
			int badVerticies = 0;
			for (int x = 0; x < pyramidVerticies[i].X(); x++) {
				for (int y = 0; y < pyramidVerticies[i].Y(); y++) {
					if (!Float3.isEqual(pyramidVerticies[i].get(x, y), refVerticies[i].get(x, y))) {
						badVerticies++;
					}
				}
			}

			int badNormals = 0;
			for (int x = 0; x < pyramidNormals[i].X(); x++) {
				for (int y = 0; y < pyramidNormals[i].Y(); y++) {
					if (!Float3.isEqual(pyramidNormals[i].get(x, y), refNormals[i].get(x, y))) {
						badNormals++;
					}
				}
			}

			System.out.printf("\tlevel %d: bad verticies=%d, bad normals=%d\n", i, badVerticies,
					badNormals);
			valid |= (badVerticies == 0 && badNormals == 0);
		}
		return valid;
	}

	public void execute() {
		for (int i = 0; i < pyramidIterations.length; i++) {
			GraphicsMath.depth2vertex(pyramidVerticies[i], pyramidDepths[i], invK[i]);
			GraphicsMath.vertex2normal(pyramidNormals[i], refVerticies[i]);
		}
	}

	public static void main(String[] args) {
		final String path = args[0];
		final int numFrames = Integer.parseInt(args[1]);

		KfusionConfig config = new KfusionConfig();

		config.setDevice(new TestingDevice());

		DepthPyramidPipeline kernel = new DepthPyramidPipeline(config);
		kernel.reset();

		int validFrames = 0;
		for (int i = 0; i < numFrames; i++) {
			System.out.printf("frame %d:\n", i);
			kernel.loadFrame(path, i);
			kernel.execute();
			boolean valid = kernel.validate(path, i);
			System.out.printf("\tframe %s valid\n", (valid) ? "is" : "is not");
			if(valid)
				validFrames++;
		}
		
		double pctValid = (((double) validFrames) / ((double) numFrames)) * 100.0;
		System.out.printf("Found %d valid frames (%.2f %%)\n",validFrames,pctValid);

	}

}
