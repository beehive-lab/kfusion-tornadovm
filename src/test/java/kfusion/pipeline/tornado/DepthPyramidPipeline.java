package kfusion.pipeline.tornado;

import kfusion.TornadoModel;
import kfusion.Utils;
import kfusion.devices.Device;
import kfusion.devices.TestingDevice;
import kfusion.pipeline.AbstractPipeline;
import tornado.collections.graphics.GraphicsMath;
import tornado.collections.types.FloatingPointError;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.runtime.api.TaskGraph;


public class DepthPyramidPipeline extends AbstractPipeline<TornadoModel> {

	public DepthPyramidPipeline(TornadoModel config) {
        super(config);
    }

    private Matrix4x4Float[]	invK;
	private ImageFloat3[]		refVerticies;
	private ImageFloat3[]		refNormals;
	
	private TaskGraph vertexGraph;

	private static String makeFilename(String path, int frame, String kernel, String variable,
			boolean isInput) {
		return String.format("%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in"
				: "out");
	}
	
	

	@Override
	public void configure(Device device) {
		super.configure(device);
		
		invK = new Matrix4x4Float[3];
		refVerticies = new ImageFloat3[3];
		refNormals = new ImageFloat3[3];
		
		vertexGraph = new TaskGraph();
		
		pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();
		
		for (int i = 0; i < pyramidIterations.length; i++) {
			invK[i] = new Matrix4x4Float();
			refVerticies[i] = pyramidVerticies[i].duplicate();
			refNormals[i] = pyramidNormals[i].duplicate();
			
			vertexGraph
				.streamIn(pyramidDepths[i], refVerticies[i],invK[i])
				.add(GraphicsMath::depth2vertex,pyramidVerticies[i], pyramidDepths[i], invK[i])
				.add(GraphicsMath::vertex2normal,pyramidNormals[i], pyramidVerticies[i])
				.streamOut(pyramidVerticies[i],pyramidNormals[i]);
		}
		
		vertexGraph.mapAllTo(config.getTornadoDevice());
	}



	private void loadFrame(String path, int index) {
	

		try {

			for (int i = 0; i < 3; i++) {
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
		System.out.println("Validation:");
		boolean valid = true;
		for (int i = 0; i < pyramidIterations.length; i++) {
			
			
			final FloatingPointError verticiesError = pyramidVerticies[i].calculateULP(refVerticies[i]);
			System.out.printf("\tlevel %d: vertex  error - %s\n",i,verticiesError.toString());
			
			final FloatingPointError normalsError = pyramidNormals[i].calculateULP(refNormals[i]);
			System.out.printf("\tlevel %d: normals error - %s\n",i,normalsError.toString());

			valid &= (verticiesError.getMaxUlp() < 5f  && normalsError.getMaxUlp() < 5f);
		}
		return valid;
	}

	public void execute() {
		vertexGraph.schedule().waitOn();
		vertexGraph.dumpTimes();
	}

	public static void main(String[] args) {
		final String path = args[0];
		final int numFrames = Integer.parseInt(args[1]);

		TornadoModel config = new TornadoModel();

		config.setDevice(new TestingDevice());

		final DepthPyramidPipeline kernel = new DepthPyramidPipeline(config);
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
