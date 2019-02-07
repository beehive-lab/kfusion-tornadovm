/*
 *    This file is part of Slambench-Tornado: A Tornado version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-tornado
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
package kfusion.tornado.ui;

import javax.swing.DefaultComboBoxModel;

import uk.ac.manchester.tornado.api.common.TornadoDevice;

public class TornadoDeviceSelection extends DefaultComboBoxModel<TornadoDevice> {

	private static final long serialVersionUID = -5945515922073691978L;

	public TornadoDeviceSelection(final TornadoDevice[] devices) {
		super(devices);
	}

	@Override
	public TornadoDevice getSelectedItem() {
		return (TornadoDevice) super.getSelectedItem();
	}

}
