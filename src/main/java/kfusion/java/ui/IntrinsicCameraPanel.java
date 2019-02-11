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
package kfusion.java.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;

import kfusion.java.common.KfusionConfig;
import kfusion.java.devices.Device;
import kfusion.java.numerics.Helper;
import uk.ac.manchester.tornado.api.collections.types.Float4;

public class IntrinsicCameraPanel<T extends KfusionConfig> extends JPanel implements ActionListener {

	private static final long	serialVersionUID	= 1625144560553365990L;
	private final JTextField	cameraFxText	= new JTextField();
	private final JTextField	cameraFyText	= new JTextField();
	private final JTextField	cameraX0Text	= new JTextField();
	private final JTextField	cameraY0Text	= new JTextField();
	private final Float4 cameraConfig;
	private final T config;

	private final JComboBox<Device> inputDeviceComboBox;
	public IntrinsicCameraPanel(final T config, final JComboBox<Device> inputDeviceComboBox) {
	    this.config = config;
		cameraConfig = new Float4();
		this.inputDeviceComboBox = inputDeviceComboBox;
		inputDeviceComboBox.addActionListener(this);
		
		((Device)inputDeviceComboBox.getSelectedItem()).updateModel(config);
		
		cameraConfig.set(config.getCamera());
		
		setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
				"Intrinsic Camera Parameters"));

		add(new JLabel("fx:"));
		cameraFxText.setColumns(5);
		add(cameraFxText);

		add(new JLabel("fy:"));
		cameraFyText.setColumns(5);
		add(cameraFyText);

		add(new JLabel("x0:"));
		cameraX0Text.setColumns(5);
		add(cameraX0Text);

		add(new JLabel("y0:"));
		cameraY0Text.setColumns(5);
		add(cameraY0Text);
		
		displayConfig();
	}
	
	private void displayConfig(){
		cameraConfig.set(config.getCamera());
		cameraFxText.setText(String.format("%.2f",cameraConfig.getX()));
		cameraFyText.setText(String.format("%.2f",cameraConfig.getY()));
		cameraX0Text.setText(String.format("%.2f",cameraConfig.getZ()));
		cameraY0Text.setText(String.format("%.2f",cameraConfig.getW()));
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		((Device)inputDeviceComboBox.getSelectedItem()).updateModel(config);
		displayConfig();
	}
	
	public void updateModel(){
		cameraConfig.setX(Helper.parseFloatValue(cameraFxText, cameraConfig.getX()));
		cameraConfig.setY(Helper.parseFloatValue(cameraFyText, cameraConfig.getY()));
		cameraConfig.setZ(Helper.parseFloatValue(cameraX0Text, cameraConfig.getZ()));
		cameraConfig.setW(Helper.parseFloatValue(cameraY0Text, cameraConfig.getW()));
		config.setCamera(cameraConfig);
	}

	public void resetConfig() {
		displayConfig();	
	}
	
}
