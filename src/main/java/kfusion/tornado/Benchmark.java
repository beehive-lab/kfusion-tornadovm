package kfusion.tornado;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import kfusion.TornadoModel;
import kfusion.devices.Device;
import kfusion.pipeline.TornadoBenchmarkPipeline;
import tornado.collections.types.Float4;

public class Benchmark {

	public static void main(String[] args) {
		

		final TornadoModel config = new TornadoModel();
		config.loadSettingsFile(args[0]);
                
                PrintStream out = System.out;
                if(args.length == 2){
                  
                    
                    try {
                        out = new PrintStream(args[1]);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        System.err.println("unable to write to file: " + args[1]);
                        System.exit(-1);
                    }
                }

		final TornadoBenchmarkPipeline pipeline =  new TornadoBenchmarkPipeline(config,out);
		
		final Device device = config.discoverDevices()[0];
		device.init();

		device.updateModel(config);
		
		// update model config here
		config.setDevice(device);
//		config.setIntegrationRate(1);
//		config.setVolumeDimensions(new Float3(4.8f,4.8f,4.8f));
		config.setCamera(new Float4(481.2f,480f,320f,240f));
			
		pipeline.reset();
		
		
		// execute benchmark
		final long start = System.nanoTime();
		pipeline.execute();
		final long stop = System.nanoTime();
		final double elapsed = (stop - start) * 1e-9;

		// finish
		System.out.printf("Summary: time=%.2f, frames=%d\n", elapsed, pipeline.getProcessedFrames());
	}

}
