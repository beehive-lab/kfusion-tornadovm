package kfusion.ui;

import javax.swing.DefaultComboBoxModel;

import tornado.common.DeviceMapping;

public class TornadoDeviceSelection extends DefaultComboBoxModel<DeviceMapping> {
	
	private static final long serialVersionUID = -5945515922073691978L;

	public TornadoDeviceSelection(final DeviceMapping[] devices){
		super(devices);
	}

	@Override
	public DeviceMapping getSelectedItem() {
		return (DeviceMapping) super.getSelectedItem();
	}

}
