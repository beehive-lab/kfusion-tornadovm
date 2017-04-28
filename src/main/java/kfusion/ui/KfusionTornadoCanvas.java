/* 
 * Copyright 2017 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kfusion.ui;

import java.awt.Dimension;

import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;

import kfusion.TornadoModel;
import kfusion.pipeline.ProxyOpenGLPipeline;

public class KfusionTornadoCanvas extends GLCanvas {
	
	private static final long	serialVersionUID	= 2058056651997252912L;
	
	@SuppressWarnings("unused")
    private final ProxyOpenGLPipeline<TornadoModel> pipeline;
	
	public KfusionTornadoCanvas(TornadoModel config, int width, int height, final TornadoConfigPanel tornadoPanel){
		GLProfile glp =  GLProfile.getDefault();
		GLCapabilities caps = new GLCapabilities(glp);
		caps.setDoubleBuffered(true);
		caps.setHardwareAccelerated(true);
		
		
		setPreferredSize(new Dimension(width,height));
		setSize(width,height);
		pipeline = new ProxyOpenGLPipeline<TornadoModel>(config, this, tornadoPanel);	
		
	}
	
}
