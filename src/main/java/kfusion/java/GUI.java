/*
 *    This file is part of Slambench-Java: A (serial) Java version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-java
 *
 *    Copyright (c) 2013-2019 APT Group, School of Computer Science,
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
package kfusion.java;

import java.awt.EventQueue;

import kfusion.java.common.KfusionConfig;
import kfusion.ui.KfusionJavaCanvas;
import kfusion.ui.WorkbenchFrame;

public class GUI {
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
			    final KfusionConfig config = new KfusionConfig();
				final WorkbenchFrame<KfusionConfig> frame  = new WorkbenchFrame<KfusionConfig>(config,new KfusionJavaCanvas<KfusionConfig>(config,660 * 2, 500));
				frame.setVisible(true);
			}
		});
	}
}
