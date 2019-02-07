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
package kfusion.devices;

import kfusion.KfusionConfig;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;

public class TestingDevice implements Device {

    final private static Float4 CAMERA = new Float4(new float[] { 531.15f, 531.15f, 640f / 2f, 480f / 2f });

    private final int width = 640;
    private final int height = 480;

    @Override
    public boolean pollVideo(ImageByte3 buffer) {
        return false;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public boolean pollDepth(ImageFloat buffer) {
        return false;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void init() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public boolean isRunning() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public <T extends KfusionConfig> void updateModel(T config) {
        config.setCamera(CAMERA);
    }

    @Override
    public boolean hasReferencePose() {

        return false;
    }

    @Override
    public Float3 getTranslation() {
        return null;
    }

    @Override
    public Float4 getRotation() {

        return null;
    }

    public String toString() {
        return String.format("Test Device <%d x %d>", width, height);
    }

    @Override
    public void skipVideoFrame() {

    }

}
