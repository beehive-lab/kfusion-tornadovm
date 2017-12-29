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
package kfusion;

import tornado.common.TornadoDevice;
import tornado.drivers.opencl.runtime.OCLTornadoDevice;

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

    @Override
    public void reset() {
        super.reset();
        useTornado = Boolean.parseBoolean(settings.getProperty("kfusion.tornado.enable",
                "False"));
        tornadoDevice = new OCLTornadoDevice(getPlatformIndex(), getDeviceIndex());
    }

    public void setTornadoDevice(TornadoDevice value) {
        tornadoDevice = value;
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
}
