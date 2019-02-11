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

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;

public class WorkbenchMenuBar extends JMenuBar{

	private static final long	serialVersionUID	= 7030713388455347289L;

	final private JMenu kfusion, view;
	
	final private JMenuItem exitItem;
	final private JMenuItem sourceKinect;
	final private JMenuItem sourceFile;
	
	public WorkbenchMenuBar(){
		
		kfusion = new JMenu("KFusion");
		final JMenuItem sourceMenu = new JMenu("Source");
		final ButtonGroup sourceGroup = new ButtonGroup();
		sourceKinect = new JRadioButtonMenuItem("Kinect");
		//sourceKinect.setEnabled(KfusionModel.config.getKinect().getDevices() > 0);
		
		sourceFile = new JRadioButtonMenuItem("File");
		
		sourceGroup.add(sourceKinect);
		sourceGroup.add(sourceFile);
		sourceMenu.add(sourceKinect);
		sourceMenu.add(sourceFile);
		
		sourceGroup.clearSelection();
		
		kfusion.add(sourceMenu);
		
		exitItem = new JMenuItem("Exit");
		//exitItem.addActionListener(this);
		//exitItem.addItemListener(this);
		
		kfusion.add(exitItem);
		
		view = new JMenu("View");
		
		final JCheckBoxMenuItem rgbCheckBox = new JCheckBoxMenuItem("RGB Images");
		final JCheckBoxMenuItem depthCheckBox = new JCheckBoxMenuItem("Depth Images");
		
		
		view.add(rgbCheckBox);
		view.add(depthCheckBox);
		
		//rgbMenu.addActionListener(this);
		//rgbMenu.addItemListener(this);
		
		
		
		add(kfusion);
		add(view);
		
	}
	
	public void addExitListener(ActionListener listener){
		exitItem.addActionListener(listener);
	}
	
	public void addSourceKinectListener(ItemListener listener){
		sourceKinect.addItemListener(listener);
	}
	
}
