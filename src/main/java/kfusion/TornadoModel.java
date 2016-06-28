package kfusion;

import kfusion.KfusionConfig;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;

public class TornadoModel extends KfusionConfig {
	private boolean					useTornado;
	private DeviceMapping			tornadoDevice;

	public TornadoModel() {
		super();
	}
	
	public boolean useTornado(){
		return  useTornado; 
	}
	
	public int getPlatformIndex(){
		return Integer.parseInt(settings.getProperty("kfusion.tornado.platform","0"));
	}
	
	public int getDeviceIndex(){
		return Integer.parseInt(settings.getProperty("kfusion.tornado.device","0"));
	}

	public void reset() {
	    super.reset();
		useTornado = Boolean.parseBoolean(settings.getProperty("kfusion.tornado.enable",
				"False"));
		tornadoDevice = new OCLDeviceMapping(getPlatformIndex(), getDeviceIndex());
	}

	public void setTornadoDevice(DeviceMapping value) {
		tornadoDevice = value;
	}
	
	public DeviceMapping getTornadoDevice(){
		return tornadoDevice;
	}

	public void setUseTornado(boolean value) {
		useTornado = value;
		
	}

    public float getMaxULP() {
       return Float.parseFloat(settings.getProperty("kfusion.maxulp","5.0"));
    }

    public boolean printKernels() {
        return Boolean.parseBoolean(settings.getProperty("kfusion.kernels.print","False"));
    }

}
