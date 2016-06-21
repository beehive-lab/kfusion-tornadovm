package kfusion.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;

import tornado.common.DeviceMapping;
import tornado.drivers.opencl.OCLDriver;
import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.runtime.TornadoRuntime;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import kfusion.TornadoModel;

public class TornadoConfigPanel extends JPanel implements ActionListener {

	private static final long serialVersionUID = 4887971237978617495L;
	final JComboBox<DeviceMapping> deviceComboBox;
	public final JCheckBox	enableTornadoCheckBox;
	
	private final TornadoModel config;
	
	public TornadoConfigPanel(final TornadoModel config) {
	    this.config = config;
		final List<DeviceMapping> tmpDevices = new ArrayList<DeviceMapping>();
		
		OCLDriver driver = null;
		final int numDrivers = TornadoRuntime.runtime.getNumDrivers();
		for(int i=0;i<numDrivers;i++){
		    if(TornadoRuntime.runtime.getDriver(i) instanceof OCLDriver){
		        driver = (OCLDriver) TornadoRuntime.runtime.getDriver(i);
		    }
		}
		
		final DeviceMapping[] devices;
		if(driver != null){
		
		for (int platformIndex = 0; platformIndex < driver.getNumPlatforms(); platformIndex++) {
			for (int deviceIndex = 0; deviceIndex < driver.getNumDevices(platformIndex); deviceIndex++) {
				final OCLDeviceMapping device = new OCLDeviceMapping(platformIndex, deviceIndex);
				//if(device.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_GPU)
					tmpDevices.add(device);
			}
		}

		devices = new DeviceMapping[tmpDevices.size()];
		tmpDevices.toArray(devices);

		} else {
		    devices = new DeviceMapping[0];
		}
		
		final TornadoDeviceSelection deviceSelectModel = new TornadoDeviceSelection(devices);
		deviceComboBox = new JComboBox<DeviceMapping>();
		deviceComboBox.setModel(deviceSelectModel);
		deviceComboBox.setEnabled(false);
		deviceComboBox.addActionListener(this);
		
		enableTornadoCheckBox = new JCheckBox("Use Tornado");
		enableTornadoCheckBox.setSelected(false);
		enableTornadoCheckBox.addActionListener(this);
		
		setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
			"Tornado Configuration"));
		
		add(enableTornadoCheckBox);
		
		add(new JLabel("Tornado Device:"));
		add(deviceComboBox);

	}

	public void updateModel(){
		config.setTornadoDevice((DeviceMapping) deviceComboBox.getSelectedItem());
		config.setUseTornado(enableTornadoCheckBox.isSelected());
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		deviceComboBox.setEnabled(enableTornadoCheckBox.isSelected());
		updateModel();

	}

}
