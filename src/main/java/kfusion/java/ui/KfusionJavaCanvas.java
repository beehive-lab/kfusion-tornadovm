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

import java.awt.Dimension;

import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;

import kfusion.java.common.KfusionConfig;
import kfusion.java.pipeline.JavaOpenGLPipeline;

public class KfusionJavaCanvas<T extends KfusionConfig> extends GLCanvas {
	
	private static final long	serialVersionUID	= 2058056651997252912L;
	
	private final JavaOpenGLPipeline<T> pipeline;
	
	public KfusionJavaCanvas(T config, int width, int height){
		GLProfile glp =  GLProfile.getDefault();
		GLCapabilities caps = new GLCapabilities(glp);
		caps.setDoubleBuffered(true);
		caps.setHardwareAccelerated(true);
		
		
		setPreferredSize(new Dimension(width,height));
		setSize(width,height);
		pipeline = new JavaOpenGLPipeline<T>(config);
		addGLEventListener(pipeline);	
	}
	
}
