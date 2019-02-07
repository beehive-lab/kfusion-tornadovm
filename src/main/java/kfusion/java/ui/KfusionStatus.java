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

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.DynamicTimeSeriesCollection;
import org.jfree.data.time.Second;

public class KfusionStatus extends JPanel {

	private static final long	serialVersionUID	= -7759035032985177508L;
	
	private DynamicTimeSeriesCollection data;
	private static final int COUNT = 2 * 60;

	public KfusionStatus(){
		data = new DynamicTimeSeriesCollection(1, 2 * 60, new Second());
		data.setTimeBase(new Second(0,0,0,1,1,2011));
		initData();
		
		JFreeChart chart = createChart();
		 ChartPanel panel = new ChartPanel(chart);
		 panel.setPreferredSize(new java.awt.Dimension(400, 200));
		 add(panel);
		 
		
		
	}
	
	private void initData(){
		final float[] values = new float[COUNT];
		data.addSeries(values, 0, "FPS");
	}
	
	private JFreeChart createChart(){
		
		 JFreeChart chart = ChartFactory.createTimeSeriesChart(
		            "Performance",  // title
		            "Time",             // x-axis label
		            "FPS",   // y-axis label
		            data,            // data
		            false,               // create legend?
		            false,               // generate tooltips?
		            false               // generate URLs?
		        );
		 
		 chart.getXYPlot().getDomainAxis().setAutoRange(true);
		 chart.getXYPlot().getRangeAxis().setAutoRange(true);
		 return chart;
	}
	
	public void append(float[] values){
		data.advanceTime();
		data.appendData(values);
		
	}
	
}
