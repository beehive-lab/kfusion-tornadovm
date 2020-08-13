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
package kfusion.tornado.common;

import kfusion.java.common.KfusionConfig;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class TornadoModel extends KfusionConfig {

	private boolean useTornado;
	private TornadoDevice tornadoDevice;

	public TornadoModel() {
		super();
	}

	public boolean useTornado() {
		return useTornado;
	}

	public int getPlatformIndex() {
		return Integer.parseInt(settings.getProperty("kfusion.tornado.platform", "0"));
	}

	public int getDeviceIndex() {
		return Integer.parseInt(settings.getProperty("kfusion.tornado.device", "0"));
	}

	public String getBackendName() {
		return settings.getProperty("kfusion.tornado.backend", "PTX");
	}

	public void setTornadoDevice(TornadoDevice device) {
		tornadoDevice = device;
	}

	public TornadoDevice getTornadoDevice() {
		return tornadoDevice;
	}

	public void setUseTornado(boolean value) {
		useTornado = value;
	}

	public float getMaxULP() {
		return Float.parseFloat(settings.getProperty("kfusion.maxulp", "5.0"));
	}

	public boolean printKernels() {
		return Boolean.parseBoolean(settings.getProperty("kfusion.kernels.print", "False"));
	}

	public int getReductionSize() {
		return Integer.parseInt(settings.getProperty("kfusion.model.reduce", "1024"));
	}

	public boolean useCustomReduce() {
		return Boolean.parseBoolean(settings.getProperty("kfusion.reduce.custom", "False"));
	}

	public boolean useSimpleReduce() {
		return Boolean.parseBoolean(settings.getProperty("kfusion.reduce.simple", "False"));
	}

	@Override
	public void reset() {
		super.reset();
		useTornado = Boolean.parseBoolean(settings.getProperty("kfusion.tornado.enable", "False"));
		tornadoDevice = TornadoRuntime.createDevice(getBackendName(), getPlatformIndex(), getDeviceIndex());
	}
}
