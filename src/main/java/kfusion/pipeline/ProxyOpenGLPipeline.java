/*
 *    This file is part of Slambench-Tornado: A Tornado version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-tornado
 *
 *    Copyright (c) 2013-2017 APT Group, School of Computer Science,
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
package kfusion.pipeline;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.media.opengl.awt.GLCanvas;

import kfusion.TornadoModel;
import kfusion.pipeline.AbstractOpenGLPipeline;
import kfusion.pipeline.JavaOpenGLPipeline;
import kfusion.pipeline.TornadoOpenGLPipeline;
import kfusion.ui.TornadoConfigPanel;
import uk.ac.manchester.tornado.common.TornadoDevice;

public class ProxyOpenGLPipeline<T extends TornadoModel> implements ActionListener {
	
	private final JavaOpenGLPipeline<T> javaPipeline;
	private final TornadoOpenGLPipeline<T> tornadoPipeline;
	private AbstractOpenGLPipeline<T> currentPipeline;
	
	private final T config;
	private final GLCanvas canvas;
	private final TornadoConfigPanel tornadoConfig;
	
	public ProxyOpenGLPipeline(final T config, final GLCanvas canvas, final TornadoConfigPanel tornadoConfig){
	    this.config = config;
	    this.canvas = canvas;
		this.tornadoConfig = tornadoConfig;
		
		
		javaPipeline = new JavaOpenGLPipeline<T>(config);
		tornadoPipeline = new TornadoOpenGLPipeline<T>(config);
		currentPipeline = javaPipeline;
		canvas.addGLEventListener(currentPipeline);
		
		tornadoConfig.enableTornadoCheckBox.addActionListener(this);
	}
	
	
	public void execute() {
		currentPipeline.execute();	
	}


	@Override
	public void actionPerformed(ActionEvent e) {
		if(tornadoConfig.enableTornadoCheckBox.isSelected() && currentPipeline != tornadoPipeline){
			config.reset();
			canvas.removeGLEventListener(currentPipeline);
			currentPipeline = tornadoPipeline;
			canvas.addGLEventListener(currentPipeline);
			
			config.setReset();
		} else if (!tornadoConfig.enableTornadoCheckBox.isSelected() && currentPipeline != javaPipeline){
		    config.reset();
			canvas.removeGLEventListener(currentPipeline);
			currentPipeline = javaPipeline;
			canvas.addGLEventListener(currentPipeline);
			config.setReset();
		}
		
	}
	
	

}
