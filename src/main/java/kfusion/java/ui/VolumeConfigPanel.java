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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;

import kfusion.java.common.KfusionConfig;
import kfusion.java.numerics.Helper;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float6;
import uk.ac.manchester.tornado.api.collections.types.Int3;

public class VolumeConfigPanel<T extends KfusionConfig> extends JPanel implements ActionListener {

	private static final long	serialVersionUID	= 1625144560553365990L;
	private final JTextField	volumeDimXText	= new JTextField();
	private final JTextField	volumeDimYText	= new JTextField();
	private final JTextField	volumeDimZText	= new JTextField();
	
	private final JTextField	volumeSizeXText	= new JTextField();
	private final JTextField	volumeSizeYText	= new JTextField();
	private final JTextField	volumeSizeZText	= new JTextField();
	
	private final JTextField	nearPlaneText	= new JTextField();
	private final JTextField	farPlaneText	= new JTextField();
	
	private final JTextField	scaleText	= new JTextField();
	
	private final Float3 volumeDims;
	private final Int3 volumeSize;
	private final T config;

	public VolumeConfigPanel(T config) {
	    this.config = config;
		volumeDims = new Float3();
		volumeSize = new Int3();
		
		setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
				"Model Configuration"));

		add(new JLabel("size (meters):"));
		add(new JLabel("x:"));
		volumeDimXText.setColumns(5);
		add(volumeDimXText);

		add(new JLabel("y:"));
		volumeDimYText.setColumns(5);
		add(volumeDimYText);

		add(new JLabel("z:"));
		volumeDimZText.setColumns(5);
		add(volumeDimZText);
		
		add(new JLabel("size (voxels):"));
		add(new JLabel("x:"));
		volumeSizeXText.setColumns(5);
		add(volumeSizeXText);

		add(new JLabel("y:"));
		volumeSizeYText.setColumns(5);
		add(volumeSizeYText);

		add(new JLabel("z:"));
		volumeSizeZText.setColumns(5);
		add(volumeSizeZText);

		add(new JLabel("Near Plane (meters):"));
		nearPlaneText.setColumns(5);
		nearPlaneText.setText(String.format("%.2f", config.getNearPlane()));
		add(nearPlaneText);
		
		add(new JLabel("Far Plane (meters):"));
		farPlaneText.setColumns(5);
		farPlaneText.setText(String.format("%.2f", config.getFarPlane()));
		add(farPlaneText);
		
		add(new JLabel("Scale:"));
		scaleText.setColumns(5);
		scaleText.setText(String.format("%d", config.getScale()));
		add(scaleText);
		
		volumeSize.set(config.getVolumeSize());
		volumeDims.set(config.getVolumeDimensions());
		
		displayConfig();
	}
	
	private void displayConfig(){
		volumeSizeXText.setText(String.format("%d",volumeSize.getX()));
		volumeSizeYText.setText(String.format("%d",volumeSize.getY()));
		volumeSizeZText.setText(String.format("%d",volumeSize.getZ()));
		
		volumeDimXText.setText(String.format("%.2f",volumeDims.getX()));
		volumeDimYText.setText(String.format("%.2f",volumeDims.getY()));
		volumeDimZText.setText(String.format("%.2f",volumeDims.getZ()));
		
		nearPlaneText.setText(String.format("%.2f",config.getNearPlane()));
		farPlaneText.setText(String.format("%.2f",config.getFarPlane()));
		scaleText.setText(String.format("%d",config.getScale()));
	
	}
	
	
	
	private void readConfig(){
		volumeSize.setX(Helper.parseIntValue(volumeSizeXText,volumeSize.getX()));
		volumeSize.setY(Helper.parseIntValue(volumeSizeYText,volumeSize.getY()));
		volumeSize.setZ(Helper.parseIntValue(volumeSizeZText,volumeSize.getZ()));
		
		volumeDims.setX(Helper.parseFloatValue(volumeDimXText,volumeDims.getX()));
		volumeDims.setY(Helper.parseFloatValue(volumeDimYText,volumeDims.getY()));
		volumeDims.setZ(Helper.parseFloatValue(volumeDimZText,volumeDims.getZ()));
		
		
	}
	
	public void resetConfig(){
		volumeSize.set(config.getVolumeSize());
		volumeDims.set(config.getVolumeDimensions());
		
		displayConfig();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		displayConfig();
	}
	
	public void updateModel(){
		readConfig();
		
		config.setVolumeDimensions(volumeDims);
		config.setVolumeSize(volumeSize);
		
		final Float6 pose = config.getInitialPose();
		pose.setS0(volumeDims.getX() / 2f);
		pose.setS1(volumeDims.getY() / 2f);
		pose.setS2(0f);
		pose.setS3(0f);
		pose.setS4(0f);
		pose.setS5(0f);
		
		config.setScale(Helper.parseIntValue(scaleText,config.getScale()));
		config.setNearPlane(Helper.parseFloatValue(nearPlaneText,config.getNearPlane()));
		config.setFarPlane(Helper.parseFloatValue(farPlaneText,config.getFarPlane()));
	}
	
}
