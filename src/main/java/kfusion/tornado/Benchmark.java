package kfusion.tornado;

import tornado.collections.types.Float3;
import tornado.collections.types.Float4;
import kfusion.TornadoModel;
import kfusion.devices.Device;
import kfusion.pipeline.TornadoBenchmarkPipeline;

public class Benchmark {

	public static void main(String[] args) {
		

		final TornadoModel config = new TornadoModel();
		config.loadSettingsFile(args[0]);

		final TornadoBenchmarkPipeline pipeline =  new TornadoBenchmarkPipeline(config);
		
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
