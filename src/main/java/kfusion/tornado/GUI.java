package kfusion.tornado;

import java.awt.EventQueue;

import kfusion.TornadoModel;
import kfusion.ui.TornadoConfigPanel;
import kfusion.ui.TornadoWorkbenchFrame;
import kfusion.ui.KfusionTornadoCanvas;

public class GUI {

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
			    final TornadoModel config = new TornadoModel();
			    final TornadoConfigPanel tornadoConfig = new TornadoConfigPanel(config);
			    final KfusionTornadoCanvas canvas = new KfusionTornadoCanvas(config,660 * 2, 500, tornadoConfig);
			    TornadoWorkbenchFrame frame  = new TornadoWorkbenchFrame(config,canvas, tornadoConfig);
				frame.setVisible(true);
			}
			
			
		});
		

	}

}
