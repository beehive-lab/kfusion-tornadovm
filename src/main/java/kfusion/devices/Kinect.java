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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.openkinect.freenect.Context;
import org.openkinect.freenect.DepthFormat;
import org.openkinect.freenect.DepthHandler;
import org.openkinect.freenect.FrameMode;
import org.openkinect.freenect.Freenect;
import org.openkinect.freenect.LedStatus;
import org.openkinect.freenect.LogHandler;
import org.openkinect.freenect.LogLevel;
import org.openkinect.freenect.VideoFormat;
import org.openkinect.freenect.VideoHandler;

import kfusion.AbstractLogger;
import kfusion.KfusionConfig;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;

public class Kinect extends AbstractLogger implements Device {

    final private static float DEPTH_SCALE_FACTOR = 1e-3f;
    final private static Float4 CAMERA = new Float4(new float[] { 531.15f, 531.15f, 640 / 2, 480 / 2 });

    private Context context;
    private org.openkinect.freenect.Device device;

    private ByteBuffer currentDepthBuffer = null;
    private ByteBuffer currentVideoBuffer = null;

    private boolean gotVideoFrame;
    private boolean gotDepthFrame;

    // private static final boolean dumpFrame = false;

    private boolean running;

    public Kinect() {
        gotVideoFrame = false;
        gotDepthFrame = false;
        running = false;
    }

    public int getDevices() {
        return context.numDevices();
    }

    public void init() {
        info("Initialising");

        context = Freenect.createContext();
        context.setLogHandler(new LogHandler() {

            @Override
            public void onMessage(org.openkinect.freenect.Device arg0, LogLevel arg1, String arg2) {
                switch (arg1) {
                    case DEBUG:
                        debug(arg2.trim());
                        break;
                    case ERROR:
                        error(arg2.trim());
                        break;
                    case FATAL:
                        fatal(arg2.trim());
                        break;
                    case WARNING:
                        warn(arg2.trim());
                        break;
                    case FLOOD:
                    case NOTICE:
                    case SPEW:
                    case INFO:
                    default:
                        info(arg2.trim());
                        break;
                }
            }
        });

        context.setLogLevel(LogLevel.FATAL);

        if (context.numDevices() > 0) {
            device = context.openDevice(0);
            device.setVideoFormat(VideoFormat.RGB);
            device.setDepthFormat(DepthFormat.MM);

            device.setLed(LedStatus.YELLOW);

            info("video format: %s", device.getVideoMode().getResolution().toString());
            info("depth format: %s", device.getDepthMode().getDepthFormat().toString());
        } else {
            fatal("no devices found");
        }

    }

    public void start() {
        context.setLogLevel(LogLevel.WARNING);
        device.startVideo(new VideoHandler() {
            @Override
            public void onFrameReceived(FrameMode mode, ByteBuffer src, int timestamp) {
                currentVideoBuffer = src;
                gotVideoFrame = true;
            }
        });

        device.startDepth(new DepthHandler() {
            @Override
            public void onFrameReceived(FrameMode mode, ByteBuffer src, int timestamp) {
                currentDepthBuffer = src;
                gotDepthFrame = true;
            }
        });
        device.setLed(LedStatus.GREEN);
        running = true;
    }

    public void stop() {
        device.stopVideo();

        device.stopDepth();

        device.setLed(LedStatus.RED);

        context.setLogLevel(LogLevel.FATAL);

        running = false;
    }

    public void shutdown() {
        info("Shutting down kinect");
        context.shutdown();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean pollVideo(ImageByte3 videoImage) {
        boolean result = false;
        if (gotVideoFrame) {
            gotVideoFrame = false;
            currentVideoBuffer.rewind();

            videoImage.loadFromBuffer(currentVideoBuffer);

            result = true;

        }
        return result;
    }

    public boolean pollDepth(ImageFloat depthImage) {
        boolean result = false;
        if (gotDepthFrame) {
            gotDepthFrame = false;

            currentDepthBuffer.order(ByteOrder.LITTLE_ENDIAN);
            currentDepthBuffer.rewind();
            int i = 0;

            while (currentDepthBuffer.hasRemaining()) {

                // need to mask off the top 5 bits of the short
                // kinect output is 11 bits, but the top 5 bits may not be zeroed?
                // libfreenect driver now does this automatically
                // byte[] b = new byte[2];
                // b[1] = currentDepthBuffer.get();
                // b[0] = currentDepthBuffer.get();
                // short s = new BigInteger(b).shortValue();
                // short s = currentDepthBuffer.getShort();

                float f = (float) currentDepthBuffer.getShort();

                // if the depth exceeds 11-bits then it has
                // wrapped around and needs to be zeroed
                // float f = (s > 0x3ff)? 0f : (float) s;

                // float f = (float) currentDepthBuffer.getShort();
                depthImage.set(i, f);
                i++;
            }

            result = true;
        }

        return result;
    }

    public FrameMode getDepthMode() {
        return device.getDepthMode();
    }

    public FrameMode getVideoMode() {
        return device.getVideoMode();
    }

    @Override
    public int getHeight() {
        return device.getDepthMode().getHeight();
    }

    @Override
    public int getWidth() {
        return device.getDepthMode().getWidth();
    }

    public String toString() {
        return String.format("Kinect[%d]", device == null ? 0 : device.getDeviceIndex());
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

        final Float3 initPositions = new Float3(0.5f, 0.5f, 0f);
        config.setInitialPositionFactors(initPositions);

        config.setVolumeDimensions(new Float3(2f, 2f, 2f));
        config.setFarPlane(2f);
    }

    @Override
    public void skipVideoFrame() {

    }

}
