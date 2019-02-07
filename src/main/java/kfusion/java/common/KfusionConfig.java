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
package kfusion.java.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import kfusion.java.devices.DepthCamera;
import kfusion.java.devices.Device;
import kfusion.java.devices.DummyKinect;
import kfusion.java.devices.Kinect;
import kfusion.java.devices.RawDevice;
import kfusion.java.devices.TUMRGBDevice;
import kfusion.java.devices.VideoCamera;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.Float6;
import uk.ac.manchester.tornado.api.collections.types.Int2;
import uk.ac.manchester.tornado.api.collections.types.Int3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;

public class KfusionConfig {
    protected static final Properties settings = new Properties(System.getProperties());

    final private static Float6 downTrans = new Float6(new float[] { 0.0f, 0f, 0f, 0f, 0f, 0.1f });

    final private static Float6 leftTrans = new Float6(new float[] { 0.0f, 0f, 0f, 0f, 0.1f, 0f });

    final private static Float6 rightTrans = new Float6(new float[] { 0.0f, 0f, 0f, 0f, -0.1f, 0f });

    final private static Float6 upTrans = new Float6(new float[] { 0.0f, 0f, 0f, 0f, 0f, -0.1f });

    private boolean debug;
    private boolean printFPS;

    private final Float3 ambient;

    private final Float4 camera;

    private float delta;

    private DepthCamera depthInput;

    private float depthScaleFactor;
    private Device device;

    private float distanceThreshold;

    private boolean dumpData;
    private float e_delta;
    private float farPlane;
    private final Int2 inputSize;

    private final int[] iterations;
    private final Float3 light;
    private float maxweight;

    private float mu;
    private float nearPlane;
    private float normalThreshold;

    private final Float3 initialPositionFactors;

    private final Float3 offset;
    private final Float6 pose;
    private final Matrix4x4Float preTrans;
    private final Float6 preTransParams;
    private boolean quit;

    private int radius;

    private boolean reset;

    private final Matrix4x4Float rot;

    private boolean rotateNegativeX;

    private boolean rotateNegativeY;

    private boolean rotatePositiveX;
    private boolean rotatePositiveY;
    private final Float6 rotParams;

    private boolean drawDepth;
    private double RSMEThreshold;
    private int scale;

    private boolean stepNegativeX;

    private boolean stepNegativeZ;

    private boolean stepPositiveX;
    private boolean stepPositiveZ;
    private float trackingThreshold;
    private final Matrix4x4Float trans;
    private final Float6 transParams;
    private VideoCamera videoInput;
    private final Float3 volumeDimensions;
    private final Int3 volumeSize;
    private int integrationRate;
    private int renderingRate;

    public KfusionConfig() {
        inputSize = new Int2();
        volumeSize = new Int3();
        volumeDimensions = new Float3();
        light = new Float3();
        ambient = new Float3();
        offset = new Float3();
        iterations = new int[3];
        rotParams = new Float6();
        transParams = new Float6();
        preTransParams = new Float6();
        initialPositionFactors = new Float3();
        pose = new Float6();
        camera = new Float4();

        trans = new Matrix4x4Float();
        preTrans = new Matrix4x4Float();
        rot = new Matrix4x4Float();

        loadSettings();

        reset();
    }

    public boolean debug() {
        return debug;
    }

    public Device[] discoverDevices() {
        final List<Device> foundDevices = new ArrayList<Device>();

        if (useRaw()) {
            final String rawFile = settings.getProperty("kfusion.raw.file");
            final int width = Integer.parseInt(settings.getProperty("kfusion.raw.width", "640"));
            final int height = Integer.parseInt(settings.getProperty("kfusion.raw.height", "480"));
            if (rawFile == null || rawFile.isEmpty()) {
                System.err.println("Please configure Raw Reader properly\nkfusion.raw.file");
            } else {
                foundDevices.add(new RawDevice(rawFile, width, height));
            }
        }

        if (useNUIM()) {
            final String nuimRoot = settings.getProperty("kfusion.nuim.root");
            final String nuimTraj = settings.getProperty("kfusion.nuim.traj");
            if (nuimRoot == null || nuimRoot.isEmpty() || nuimTraj == null || nuimTraj.isEmpty()) {
                System.err.println("Please configure NUIM dataset properly\nkfusion.nuim.root AND kfusion.nuim.traj");
            } else {
                foundDevices.add(new TUMRGBDevice(nuimRoot, nuimTraj));
            }
        }

        if (useFreenect()) {
            foundDevices.add(new Kinect());
        }

        if (useDummy()) {
            foundDevices.add(new DummyKinect());
        }

        final Device[] devices = new Device[foundDevices.size()];
        foundDevices.toArray(devices);

        return devices;
    }

    public boolean drawDepth() {
        return drawDepth;
    }

    public Float3 getAmbient() {
        return ambient;
    }

    public boolean getAndClearReset() {
        final boolean result = reset;
        reset = false;
        return result;
    }

    public boolean getAndClearRotateNegativeX() {
        final boolean result = rotateNegativeX;
        if (rotateNegativeX)
            rotateNegativeX = false;
        return result;
    }

    public boolean getAndClearRotateNegativeY() {
        final boolean result = rotateNegativeY;
        if (rotateNegativeY)
            rotateNegativeY = false;
        return result;
    }

    public boolean getAndClearRotatePositiveX() {
        final boolean result = rotatePositiveX;
        if (rotatePositiveX)
            rotatePositiveX = false;
        return result;
    }

    public boolean getAndClearRotatePositiveY() {
        final boolean result = rotatePositiveY;
        if (rotatePositiveY)
            rotatePositiveY = false;
        return result;
    }

    public boolean getAndClearStepNegativeX() {
        final boolean result = stepNegativeX;
        if (stepNegativeX)
            stepNegativeX = false;
        return result;
    }

    public boolean getAndClearStepNegativeZ() {
        final boolean result = stepNegativeZ;
        if (stepNegativeZ)
            stepNegativeZ = false;
        return result;
    }

    public boolean getAndClearStepPositiveX() {
        final boolean result = stepPositiveX;
        if (stepPositiveX)
            stepPositiveX = false;
        return result;
    }

    public boolean getAndClearStepPositiveZ() {
        final boolean result = stepPositiveZ;
        if (stepPositiveZ)
            stepPositiveZ = false;
        return result;
    }

    public Float4 getCamera() {
        return camera;
    }

    public float getDelta() {
        return delta;
    }

    public DepthCamera getDepthInput() {
        return depthInput;
    }

    public float getDepthScaleFactor() {
        return depthScaleFactor;
    }

    public Device getDevice() {
        return device;
    }

    public float getDistanceThreshold() {
        return distanceThreshold;
    }

    public Float6 getDowntrans() {
        return downTrans;
    }

    public float getE_delta() {
        return e_delta;
    }

    public float getFarPlane() {
        return farPlane;
    }

    public Float6 getInitialPose() {
        Float3 pos = Float3.mult(initialPositionFactors, volumeDimensions);
        pose.setS0(pos.getX());
        pose.setS1(pos.getY());
        pose.setS2(pos.getZ());
        return pose;
    }

    public Int2 getInputSize() {
        return inputSize;
    }

    public int getIntegrationRate() {
        return integrationRate;
    }

    public int[] getIterations() {
        return iterations;
    }

    public Float6 getLefttrans() {
        return leftTrans;
    }

    public Float3 getLight() {
        return light;
    }

    public float getMaxweight() {
        return maxweight;
    }

    public float getMu() {
        return mu;
    }

    public float getNearPlane() {
        return nearPlane;
    }

    public float getNormalThreshold() {
        return normalThreshold;
    }

    public Float3 getOffset() {
        return offset;
    }

    public Float6 getPoseParams() {
        return pose;
    }

    public Matrix4x4Float getPreTrans() {
        return preTrans;
    }

    public Float6 getPreTransParams() {
        return preTransParams;
    }

    public boolean getQuit() {
        return quit;
    }

    public int getRadius() {
        return radius;
    }

    public int getRenderingRate() {
        return renderingRate;
    }

    public Float6 getRighttrans() {
        return rightTrans;
    }

    public Matrix4x4Float getRot() {
        return rot;
    }

    public Float6 getRotParams() {
        return rotParams;
    }

    public double getRSMEThreshold() {
        return RSMEThreshold;
    }

    public int getScale() {
        return scale;
    }

    public float getTrackingThreshold() {
        return trackingThreshold;
    }

    public Matrix4x4Float getTrans() {
        return trans;
    }

    public Float6 getTransParams() {
        return transParams;
    }

    public Float6 getUptrans() {
        return upTrans;
    }

    public VideoCamera getVideoInput() {
        return videoInput;
    }

    public Float3 getVolumeDimensions() {
        return volumeDimensions;
    }

    public Int3 getVolumeSize() {
        return volumeSize;
    }

    private void loadSettings() {
        String KFUSION_ROOT = System.getenv("KFUSION_ROOT");
        if (KFUSION_ROOT == null) {
            KFUSION_ROOT = ".";
        }
        boolean loaded = loadSettingsFile(KFUSION_ROOT + "/conf/kfusion.settings");
        if (!loaded) {
            System.err.println("Could not find kfusion settings in ./conf/kfusion.settings");
        }
    }

    public boolean loadSettingsFile(String filename) {
        boolean result = false;
        final File file = new File(filename);
        if (file.exists() && file.isFile()) {
            try {
                settings.load(new FileInputStream(file));
                if (settings.getProperty("kfusion.debug") != null)
                    debug = Boolean.parseBoolean(settings.getProperty("kfusion.debug"));
                reset();
                result = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public boolean printFPS() {
        return printFPS;
    }

    public void reset() {

        final float posX = Float.parseFloat(settings.getProperty("kfusion.model.offset.x", "0.5"));
        final float posY = Float.parseFloat(settings.getProperty("kfusion.model.offset.y", "0.5"));
        final float posZ = Float.parseFloat(settings.getProperty("kfusion.model.offset.z", "0"));

        initialPositionFactors.setX(posX);
        initialPositionFactors.setY(posY);
        initialPositionFactors.setZ(posZ);

        offset.setX(posX);
        offset.setY(posY);
        offset.setZ(posZ);

        pose.setS0(volumeDimensions.getX() / 2f);
        pose.setS1(volumeDimensions.getY() / 2f);
        pose.setS2(0f);
        pose.setS3(0f);
        pose.setS4(0f);
        pose.setS5(0f);

        dumpData = false;

        reset = true;
        quit = false;

        scale = Integer.parseInt(settings.getProperty("kfusion.model.scale", "2"));

        volumeSize.setX(Integer.parseInt(settings.getProperty("kfusion.model.volumesize.x", "256")));
        volumeSize.setY(Integer.parseInt(settings.getProperty("kfusion.model.volumesize.y", "256")));
        volumeSize.setZ(Integer.parseInt(settings.getProperty("kfusion.model.volumesize.z", "256")));

        volumeDimensions.setX(Float.parseFloat(settings.getProperty("kfusion.model.volumedims.x", "5.0")));
        volumeDimensions.setY(Float.parseFloat(settings.getProperty("kfusion.model.volumedims.y", "5.0")));
        volumeDimensions.setZ(Float.parseFloat(settings.getProperty("kfusion.model.volumedims.z", "5.0")));

        nearPlane = Float.parseFloat(settings.getProperty("kfusion.model.nearplane", "0.4"));
        farPlane = Float.parseFloat(settings.getProperty("kfusion.model.farplane", "4.0"));

        mu = Float.parseFloat(settings.getProperty("kfusion.model.mu", "0.1"));
        maxweight = Float.parseFloat(settings.getProperty("kfusion.model.maxweight", "100.0"));

        radius = 2;
        delta = Float.parseFloat(settings.getProperty("kfusion.model.delta", "4.0"));
        e_delta = Float.parseFloat(settings.getProperty("kfusion.model.edelta", "0.1"));

        distanceThreshold = Float.parseFloat(settings.getProperty("kfusion.model.distancethreshold", "0.1"));
        normalThreshold = Float.parseFloat(settings.getProperty("kfusion.model.normalthreshold", "0.8"));

        trackingThreshold = Float.parseFloat(settings.getProperty("kfusion.model.trackingthreshold", "0.15"));
        RSMEThreshold = Double.parseDouble(settings.getProperty("kfusion.model.rsmethreshold", "2e-2"));

        light.setX(Float.parseFloat(settings.getProperty("kfusion.model.light.x", "1.0")));
        light.setY(Float.parseFloat(settings.getProperty("kfusion.model.light.y", "1.0")));
        light.setZ(Float.parseFloat(settings.getProperty("kfusion.model.light.z", "-1.0")));

        ambient.setX(Float.parseFloat(settings.getProperty("kfusion.model.ambient.x", "0.1")));
        ambient.setY(Float.parseFloat(settings.getProperty("kfusion.model.ambient.y", "0.1")));
        ambient.setZ(Float.parseFloat(settings.getProperty("kfusion.model.ambient.z", "0.1")));

        iterations[0] = Integer.parseInt(settings.getProperty("kfusion.model.pyramid.0.iterations", "10"));
        iterations[1] = Integer.parseInt(settings.getProperty("kfusion.model.pyramid.1.iterations", "5"));
        iterations[2] = Integer.parseInt(settings.getProperty("kfusion.model.pyramid.2.iterations", "4"));

        integrationRate = Integer.parseInt(settings.getProperty("kfusion.model.integrationrate", "1"));
        renderingRate = Integer.parseInt(settings.getProperty("kfusion.model.renderingrate", "4"));
        drawDepth = Boolean.parseBoolean(settings.getProperty("kfusion.model.drawDepth", "True"));

        printFPS = Boolean.parseBoolean(settings.getProperty("kfusion.printfps", "False"));
    }

    public void rotateNegativeX() {
        rotateNegativeX = true;
    }

    public void rotateNegativeY() {
        rotateNegativeY = true;
    }

    public void rotatePositiveX() {
        rotatePositiveX = true;
    }

    public void rotatePositiveY() {
        rotatePositiveY = true;
    }

    public void setAmbient(final Float3 value) {
        ambient.set(value);
    }

    public void setCamera(final Float4 value) {
        camera.set(value);
    }

    public void setDelta(final float value) {
        delta = value;
    }

    public void setDepthInput(final DepthCamera value) {
        depthInput = value;
        inputSize.setX(depthInput.getWidth());
        inputSize.setY(depthInput.getHeight());
    }

    public void setDepthScaleFactor(final float value) {
        depthScaleFactor = value;
    }

    public void setDevice(final Device value) {
        device = value;
    }

    public void setDistanceThreshold(final float value) {
        distanceThreshold = value;
    }

    public void setDrawDepth(boolean value) {
        drawDepth = value;
    }

    public void setE_delta(final float value) {
        e_delta = value;
    }

    public void setFarPlane(final float value) {
        farPlane = value;
    }

    public void setInitialPositionFactors(Float3 value) {
        initialPositionFactors.set(value);
        offset.set(value);
    }

    public void setInputSize(final Int2 value) {
        inputSize.set(value);
    }

    public void setIntegrationRate(int integrationRate) {
        this.integrationRate = integrationRate;
    }

    public void setIterations(final int[] value) {
        if (value.length == iterations.length) {
            for (int i = 0; i < value.length; i++) {
                iterations[i] = value[i];
            }
        }
    }

    public void setLight(final Float3 value) {
        light.set(value);
    }

    public void setMaxweight(final float value) {
        maxweight = value;
    }

    public void setMu(final float value) {
        mu = value;
    }

    public void setNearPlane(final float value) {
        nearPlane = value;
    }

    public void setNormalThreshold(final float value) {
        normalThreshold = value;
    }

    public void setPoseParams(final Float6 value) {
        pose.set(value);
    }

    public void setPreTrans(final Matrix4x4Float value) {
        preTrans.set(value);
    }

    public void setPreTransParams(final Float6 value) {
        preTransParams.set(value);
    }

    public void setQuit() {
        quit = true;
    }

    public void setRadius(final int value) {
        radius = value;
    }

    public void setRenderingRate(int value) {
        this.renderingRate = value;
    }

    public void setReset() {
        reset = true;
    }

    public void setRot(final Matrix4x4Float value) {
        rot.set(value);
    }

    public void setRotParams(final Float6 value) {
        rotParams.set(value);
    }

    public void setScale(final int value) {
        scale = value;
    }

    public void setTrackingThreshold(final float value) {
        trackingThreshold = value;
    }

    public void setTrans(final Matrix4x4Float value) {
        trans.set(value);
    }

    public void setTransParams(final Float6 value) {
        transParams.set(value);
    }

    public void setVideoInput(final VideoCamera value) {
        videoInput = value;
    }

    public void setVolumeDimensions(final Float3 value) {
        volumeDimensions.set(value);
    }

    public void setVolumeSize(final Int3 value) {
        volumeSize.set(value);
    }

    public boolean shouldDumpData() {
        return dumpData;
    }

    public void stepNegativeX() {
        stepNegativeX = true;
    }

    public void stepNegativeZ() {
        stepNegativeZ = true;
    }

    public void stepPositiveX() {
        stepPositiveX = true;
    }

    public void stepPositiveZ() {
        stepPositiveZ = true;
    }

    public void toggleDebug() {
        debug = !debug;
    }

    public boolean useDummy() {
        return Boolean.parseBoolean(settings.getProperty("kfusion.dummy.enable", "False"));
    }

    public boolean useFreenect() {
        return Boolean.parseBoolean(settings.getProperty("kfusion.freenect.enable", "False"));
    }

    public boolean useNUIM() {
        return Boolean.parseBoolean(settings.getProperty("kfusion.nuim.enable", "False"));
    }

    public boolean useRaw() {
        return Boolean.parseBoolean(settings.getProperty("kfusion.raw.enable", "False"));
    }

}
