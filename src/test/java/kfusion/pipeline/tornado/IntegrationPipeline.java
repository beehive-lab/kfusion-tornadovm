package kfusion.pipeline.tornado;


import tornado.collections.math.TornadoMath;
import tornado.collections.graphics.GraphicsMath;
import tornado.collections.matrix.MatrixFloatOps;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.Short2;
import tornado.collections.types.VolumeShort2;
import tornado.common.enums.Access;
import tornado.runtime.api.PrebuiltTask;
import tornado.runtime.api.TaskGraph;
import tornado.runtime.api.TaskUtils;
import kfusion.TornadoModel;
import kfusion.Utils;
import kfusion.tornado.algorithms.Integration;
import kfusion.devices.Device;
import kfusion.devices.TestingDevice;
import kfusion.pipeline.AbstractPipeline;

public class IntegrationPipeline extends AbstractPipeline<TornadoModel> {
	
	public IntegrationPipeline(TornadoModel config) {
        super(config);
    }

    private VolumeShort2 refVolume;
	
	private TaskGraph graph;
	
	@Override
	public void configure(Device device) {
		super.configure(device);
		
		graph = new TaskGraph();
		
		final PrebuiltTask integrate = TaskUtils.createTask(
		           "integrate",
		           "./opencl/integrate.cl",
		           new Object[]{scaledDepthImage, invTrack, K, volumeDims,
		                    volume, mu, maxWeight},
		           new Access[]{Access.READ,Access.READ,Access.READ,Access.READ,Access.READ_WRITE,Access.READ,Access.READ},
		           config.getTornadoDevice(),
		           new int[]{volume.X(),volume.Y()});
		        
		
		graph
			.streamIn(volume,scaledDepthImage,invTrack,K)
//			.add(Integration::integrate,scaledDepthImage, invTrack, K, volumeDims,
//					volume, mu, maxWeight)
			.add(integrate)
			.streamOut(volume)
			.mapAllTo(config.getTornadoDevice());
	}


	private static String makeFilename(String path, int frame, String kernel, String variable, boolean isInput){
		return String.format("%s/%04d_%s_%s_%s",path,frame,kernel,variable,(isInput) ? "in" : "out");
	}
	
	
	private void loadFrame(String path, int index){
		try {
			refVolume = volume.duplicate();
			
			Utils.loadData(makeFilename(path,index,"integration","volume",true), volume.asBuffer());
			
			Utils.loadData(makeFilename(path,index,"integration","volume",false), refVolume.asBuffer());
			Utils.loadData(makeFilename(path,index,"integration","volumeDims",true), volumeDims.asBuffer());
			Utils.loadData(makeFilename(path,index,"integration","k",true), scaledCamera.asBuffer());
			GraphicsMath.getCameraMatrix(scaledCamera, K);
			Utils.loadData(makeFilename(path,index,"integration","pose",true), currentView.getPose().asBuffer());
			Utils.loadData(makeFilename(path,index,"integration","depths",true), scaledDepthImage.asBuffer());
		
			invTrack.set(currentView.getPose());
			MatrixFloatOps.inverse(invTrack);
			
			Matrix4x4Float refInversePose = new Matrix4x4Float();
			Utils.loadData(makeFilename(path,index,"integration","inversePose",true), refInversePose.asBuffer());
			
			Matrix4x4Float refK = new Matrix4x4Float();
			Utils.loadData(makeFilename(path,index,"integration","cameraMatrix",true), refK.asBuffer());
			
			//System.out.printf("ref inv pose:\n%s\n",refInversePose.toString(FloatOps.fmt4em));
			//System.out.printf("ref K       :\n%s\n",refK.toString(FloatOps.fmt4em));
			
		} catch(Exception e){
			e.printStackTrace();
		}
	}
	
	
	@Override
	public void execute() {
		
//	    Integration.integrate(scaledDepthImage, invTrack, K, volumeDims,
//      volume, mu, maxWeight);
		graph.schedule().waitOn();
		graph.dumpTimes();

	}
	
	public boolean validate(String path, int index) {
		
		int errors=0;
		for(int y=0;y<volume.Y();y++)
			for(int x=0;x<volume.X();x++)
				for(int z=0;z<volume.Z();z++){
					if(!TornadoMath.isEqual(volume.get(x, y, z).asBuffer().array(), refVolume.get(x, y, z).asBuffer().array())){
						final Short2 calc = volume.get(x,y,z);
						final Short2 ref  = refVolume.get(x,y,z);
						//if(x==83 && y==68 && z== 203)
							System.out.printf("[%d, %d, %d] error: %s != %s\n",x,y,z,calc.toString(),ref.toString());
						errors++;
					}
				}
		double pctBad = (((double) errors ) / ((double) volume.X() * volume.Y() * volume.Z())) * 100.0;
		System.out.printf("\tfound %d bad values ( %.2f %%) \n",errors,pctBad);
		
		return errors == 0;
	}

	public static void main(String[] args) {
		final String path = args[0];
		final int numFrames = Integer.parseInt(args[1]);
		TornadoModel config = new TornadoModel();

		config.setDevice(new TestingDevice());
		
		IntegrationPipeline kernel = new IntegrationPipeline(config);
		kernel.reset();
		
		int validFrames = 0;
//		for (int i = 0; i < numFrames; i++) {
		int i = 31;
			System.out.printf("frame %d:\n", i);
			kernel.loadFrame(path, i);
			kernel.execute();
			boolean valid = kernel.validate(path, i);
			System.out.printf("\tframe %s valid\n", (valid) ? "is" : "is not");
			if(valid)
				validFrames++;
//		}
		
		double pctValid = (((double) validFrames) / ((double) numFrames)) * 100.0;
		System.out.printf("Found %d valid frames (%.2f %%)\n",validFrames,pctValid);
		

	}

}
