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
