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
package kfusion.java.devices;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import kfusion.java.common.AbstractLogger;
import kfusion.java.common.KfusionConfig;
import kfusion.java.common.Utils;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.data.nativetypes.FloatArray;

public class DummyKinect extends AbstractLogger implements Device {

    final private String FILE_PATH = "/Users/jamesclarkson/git/kfusion_cpp/build";

    final private String KINECT_PREFIX = "rawdepth.in.";

    final private static float DEPTH_SCALE_FACTOR = 1e-3f;
    final private static Float4 CAMERA = new Float4(531.15f, 531.15f, 640 / 2, 480 / 2);

    private int frameIndex;
    private byte[] byteArray;
    private ByteBuffer byteBuffer;
    private boolean running;

    public DummyKinect() {
        running = false;
    }

    public void init() {
        info("Initialising");
        frameIndex = 0;
        byteArray = new byte[640 * 480 * 2];
        byteBuffer = ByteBuffer.wrap(byteArray);
    }

    public boolean pollVideo(ImageByte3 videoImage) {
        boolean result = false;
        return result;
    }

    public boolean pollDepth(ImageFloat depthImage) {
        boolean result = false;

        try {
            Utils.loadData(String.format("%s/%s%04d", FILE_PATH, KINECT_PREFIX, frameIndex), byteArray);
            frameIndex++;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        byteBuffer.rewind();
        int i = 0;

        while (byteBuffer.hasRemaining()) {

            // byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            byte[] b = new byte[2];
            b[1] = byteBuffer.get();
            b[0] = byteBuffer.get();

            float f = (float) new BigInteger(b).shortValue();

            depthImage.set(i, f);
            i++;
        }

        if (i > 0) {
            debug("pollDepth: read %d elements", i);
            result = true;
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public int getHeight() {
        return 480;
    }

    @Override
    public int getWidth() {
        return 640;
    }

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
    }

    public void shutdown() {

    }

    public boolean isRunning() {
        return running;
    }

    public String toString() {
        return "Dummy Kinect";
    }

    @Override
    public boolean hasReferencePose() {
        return false;
    }

    @Override
    public Float3 getTranslation() {
        return new Float3();
    }

    @Override
    public Float4 getRotation() {
        return new Float4();
    }

    @Override
    public <T extends KfusionConfig> void updateModel(T config) {
        config.setDepthScaleFactor(DEPTH_SCALE_FACTOR);
        config.setCamera(CAMERA);
    }

    @Override
    public void skipVideoFrame() {

    }
}
