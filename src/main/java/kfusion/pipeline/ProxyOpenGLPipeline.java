package kfusion.pipeline;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.media.opengl.awt.GLCanvas;

import kfusion.TornadoModel;
import kfusion.pipeline.AbstractOpenGLPipeline;
import kfusion.pipeline.JavaOpenGLPipeline;
import kfusion.pipeline.TornadoOpenGLPipeline;
import kfusion.ui.TornadoConfigPanel;

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
