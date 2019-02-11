/*
 *  This file is part of Tornado-KFusion: A Java version of the KFusion computer vision
 *  algorithm running on TornadoVM.
 *  URL: https://github.com/beehive-lab/kfusion-tornadovm
 *
 *  Copyright (c) 2013-2019 APT Group, School of Computer Science,
 *  The University of Manchester
 *
 *  This work is partially supported by EPSRC grants Anyscale EP/L000725/1, 
 *  PAMELA EP/K008730/1, and EU Horizon 2020 E2Data 780245.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package kfusion.tornado.ui;

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

import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class TornadoConfigPanel extends JPanel implements ActionListener {

	private static final long serialVersionUID = 4887971237978617495L;
	final JComboBox<TornadoDevice> deviceComboBox;
	public final JCheckBox enableTornadoCheckBox;

	private final TornadoModel config;

	public TornadoConfigPanel(final TornadoModel config) {
		this.config = config;
		final List<TornadoDevice> tmpDevices = new ArrayList<>();
		
		TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);

		final TornadoDevice[] devices;
		if (driver != null) {
			
			for (int devIndex = 0; devIndex < driver.getDeviceCount(); devIndex++) {
				final TornadoDevice device = driver.getDevice(devIndex);
				tmpDevices.add(device);
			}
			
			devices = new TornadoDevice[tmpDevices.size()];
			tmpDevices.toArray(devices);

		} else {
			devices = new TornadoDevice[0];
		}

		final TornadoDeviceSelection deviceSelectModel = new TornadoDeviceSelection(devices);
		deviceComboBox = new JComboBox<>();
		deviceComboBox.setModel(deviceSelectModel);
		deviceComboBox.setEnabled(false);
		deviceComboBox.addActionListener(this);

		enableTornadoCheckBox = new JCheckBox("Use Tornado");
		enableTornadoCheckBox.setSelected(false);
		enableTornadoCheckBox.addActionListener(this);

		setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED), "Tornado Configuration"));
		add(enableTornadoCheckBox);
		add(new JLabel("Tornado Device:"));
		add(deviceComboBox);
	}

	public void updateModel() {
		config.setTornadoDevice((TornadoDevice)deviceComboBox.getSelectedItem());
		config.setUseTornado(enableTornadoCheckBox.isSelected());
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		deviceComboBox.setEnabled(enableTornadoCheckBox.isSelected());
		updateModel();
	}
}
