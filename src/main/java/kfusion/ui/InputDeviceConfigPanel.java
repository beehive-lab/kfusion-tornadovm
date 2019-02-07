/*
 *    This file is part of Slambench-Java: A (serial) Java version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-java
 *
 *    Copyright (c) 2013-2019 APT Group, School of Computer Science,
 *    The University of Manchester
 *
 *    This work is partially supported by EPSRC grants:
 *    Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *    Authors: James Clarkson
 */
package kfusion.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;

import kfusion.KfusionConfig;
import kfusion.devices.Device;

public class InputDeviceConfigPanel<T extends KfusionConfig> extends JPanel implements ActionListener {

	private static final long serialVersionUID = 4887971237978617495L;
	
	private final InputDeviceSelection  inputDeviceSelectModel;
	private final JComboBox<Device> inputDeviceComboBox;
	private final T config;
	
	public InputDeviceConfigPanel(final T config, final JButton startButton, final JButton resetButton, ActionListener actionListener) {
	    this.config = config;
		inputDeviceSelectModel = new InputDeviceSelection(config.discoverDevices());
		
		inputDeviceComboBox = new JComboBox<Device>();
		inputDeviceComboBox.setModel(inputDeviceSelectModel);
		
		inputDeviceComboBox.addActionListener(actionListener);
		
		setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
			"Input Configuration"));
		
		add(inputDeviceComboBox);
		add(startButton);
		add(resetButton);
	}
	
	public JComboBox<Device> getComboBox(){
		return inputDeviceComboBox;
	}

	public void updateModel(){
		config.setDevice((Device) inputDeviceComboBox.getSelectedItem()); 
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		updateModel();
	}

}
