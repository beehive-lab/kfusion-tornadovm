package kfusion.pipeline.java;

import tornado.collections.graphics.GraphicsMath;
import tornado.collections.types.FloatingPointError;
import tornado.collections.types.ImageFloat3;
import kfusion.KfusionConfig;
import kfusion.Utils;
import kfusion.devices.TestingDevice;
import kfusion.pipeline.AbstractPipeline;

public class RaycastingPipeline extends AbstractPipeline<KfusionConfig> {

	public RaycastingPipeline(KfusionConfig config) {
        super(config);
    }

    private ImageFloat3 refVerticies;
	private ImageFloat3 refNormals;
	
	private static String makeFilename(String path, int frame, String kernel, String variable, boolean isInput){
		return String.format("%s/%04d_%s_%s_%s",path,frame,kernel,variable,(isInput) ? "in" : "out");
	}
	
	private void loadFrame(String path, int index){
		try {
			refVerticies = referenceView.getVerticies().duplicate();
			refNormals = referenceView.getNormals().duplicate();
			
			Utils.loadData(makeFilename(path,index,"raycasting","volume",true), volume.asBuffer());
			Utils.loadData(makeFilename(path,index,"raycasting","volumeDims",true), volumeDims.asBuffer());
			Utils.loadData(makeFilename(path,index,"raycasting","k",true), scaledCamera.asBuffer());
			GraphicsMath.getCameraMatrix(scaledCamera, K);
			GraphicsMath.getInverseCameraMatrix(scaledCamera, scaledInvK);
			Utils.loadData(makeFilename(path,index,"raycasting","pose",true), currentView.getPose().asBuffer());
			
			Utils.loadData(makeFilename(path,index,"raycasting","vertex",false), refVerticies.asBuffer());
			Utils.loadData(makeFilename(path,index,"raycasting","normal",false), refNormals.asBuffer());
				
			//System.out.printf("ref inv pose:\n%s\n",refInversePose.toString(FloatOps.fmt4em));
			//System.out.printf("ref K       :\n%s\n",refK.toString(FloatOps.fmt4em));
			
		} catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public boolean validate(String path, int index) {
		
		FloatingPointError vertexError = referenceView.getVerticies().calculateULP(refVerticies);
		System.out.printf("\tvertex errors: %s\n",vertexError.toString());

		FloatingPointError normalError = referenceView.getNormals().calculateULP(refNormals);
		System.out.printf("\tnormal errors: %sf\n",normalError);

		return (vertexError.getMaxUlp() > 5f || normalError.getMaxUlp() > 5f) ? false : true;
		
		}
	
	@Override
	public void execute() {
		
		updateReferenceView();

	}

	public static void main(String[] args) {
		final String path = args[0];
		final int numFrames = Integer.parseInt(args[1]);
		KfusionConfig config = new KfusionConfig();

		config.setDevice(new TestingDevice());
		
		RaycastingPipeline kernel = new RaycastingPipeline(config);
		kernel.reset();
		
		int validFrames = 0;
		for (int i = 0; i < numFrames; i++) {
		//int i = 5;
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
