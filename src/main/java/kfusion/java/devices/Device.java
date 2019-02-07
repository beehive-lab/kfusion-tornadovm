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
package kfusion.java.devices;

import kfusion.java.common.KfusionConfig;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;

public interface Device extends VideoCamera, DepthCamera {

    public void start();

    public void stop();

    public void init();

    public void shutdown();

    public boolean isRunning();

    public <T extends KfusionConfig> void updateModel(T config);

    public boolean hasReferencePose();

    public Float3 getTranslation();

    public Float4 getRotation();
}
