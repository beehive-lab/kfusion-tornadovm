package kfusion.ui;

import com.jogamp.opengl.util.Animator;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JPanel;
import kfusion.TornadoModel;
import kfusion.devices.Device;

public class TornadoModelConfigPanel extends JPanel implements ActionListener{
	
	private static final long	serialVersionUID	= 4887971237978617495L;
	
	final InputDeviceConfigPanel<TornadoModel> inputDeviceConfig;
	final VolumeConfigPanel<TornadoModel> 	volumeConfig;
	final IntrinsicCameraPanel<TornadoModel> cameraConfig;
	final TornadoConfigPanel tornadoConfig;
	
	public TornadoModelConfigPanel(final TornadoModel config, final Animator animator, TornadoConfigPanel tornadoPanel){
		tornadoConfig = tornadoPanel;
		final JButton resetButton = new JButton("Reset");
		final JButton startButton = new JButton("Start");
		
		inputDeviceConfig = new InputDeviceConfigPanel<TornadoModel>(config,startButton, resetButton, this);
		
		volumeConfig = new VolumeConfigPanel<TornadoModel>(config);
		cameraConfig = new IntrinsicCameraPanel<TornadoModel>(config,inputDeviceConfig.getComboBox());	
		
		resetButton.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent e) {
				animator.stop();
				config.reset();
				
			//	if(config.getDevice() == null){
				
				if(config.getDevice() != null){
					config.getDevice().stop();
					config.getDevice().shutdown();
				}
				
				//final Device device = (Device) inputDeviceConfig.getComboBox().getSelectedItem();
				config.setDevice(null); 
				
		
			//	}
				
				config.setReset();
			}
			
		});
		
		startButton.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent e) {
				if(startButton.getText().equals("Stop")){
//					if(animator.isStarted())
//						animator.stop();
					
					final Device currentDevice = config.getDevice();
					if(currentDevice.isRunning()){
						currentDevice.stop();
//						currentDevice.shutdown();
					}
					
//					KfusionModel.config.setDevice(null);
//					KfusionModel.config.reset();
				
					startButton.setText("Start");
				} else {
					
					if(config.getDevice() == null){
						final Device device = (Device) inputDeviceConfig.getComboBox().getSelectedItem();
						if(device.isRunning()){
							device.stop();
							device.shutdown();
						}
						
						config.setDevice(device);
						
						device.init();
						device.updateModel(config);
						volumeConfig.updateModel();
						cameraConfig.updateModel();
						tornadoConfig.updateModel();
						config.setReset();	
					}
					
					if(!animator.isStarted())
						animator.start();
					
					config.getDevice().start();	
					
					startButton.setText("Stop");
				}
			}
			
		});
		
		add(inputDeviceConfig);
		add(tornadoConfig);
		add(cameraConfig);
		add(volumeConfig);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		volumeConfig.resetConfig();
		cameraConfig.resetConfig();
	}

}
