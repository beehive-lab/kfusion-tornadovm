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
package kfusion.java.pipeline;

import kfusion.java.algorithms.IterativeClosestPoint;
import kfusion.java.algorithms.Raycast;
import kfusion.java.algorithms.TrackingResult;
import kfusion.java.common.AbstractLogger;
import kfusion.java.common.KfusionConfig;
import kfusion.java.devices.DepthCamera;
import kfusion.java.devices.Device;
import kfusion.java.devices.VideoCamera;
import kfusion.java.numerics.Helper;
import kfusion.tornado.algorithms.FloatSE3;
import kfusion.tornado.algorithms.GraphicsMath;
import kfusion.tornado.algorithms.ImagingOps;
import kfusion.tornado.algorithms.Renderer;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageByte4;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat8;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Int2;
import uk.ac.manchester.tornado.api.types.vectors.Int3;
import uk.ac.manchester.tornado.api.types.vectors.Short2;
import uk.ac.manchester.tornado.api.types.volumes.VolumeShort2;
import uk.ac.manchester.tornado.matrix.MatrixFloatOps;
import uk.ac.manchester.tornado.matrix.MatrixMath;

public abstract class AbstractPipeline<T extends KfusionConfig> extends AbstractLogger {

    protected static final float INVALID = -2f;

    protected static final void updateRotation(final Matrix4x4Float m, final FloatArray x) {
        final Matrix4x4Float transform = new FloatSE3(x).toMatrix4();
        final Matrix4x4Float current = m.duplicate();

        // TODO simplify this
        MatrixMath.sgemm(transform, current, m);
    }

    protected long accumulatedTime;
    protected final Float3 ambient;

    protected final Float4 camera;
    protected final Float4 scaledCamera;
    protected final Float3 cameraDelta;
    protected final Int2 inputSize;
    protected final Int2 scaledInputSize;

    protected final T config;

    /**
     * current view
     */
    protected View currentView;

    protected float delta;
    /**
     * depth inputs
     */
    protected DepthCamera depthCamera;

    protected ImageFloat depthImageInput;
    protected float depthScaleFactor;
    /**
     * ICP configurations
     */
    protected float distanceThreshold;

    protected float eDelta;
    protected float farPlane;
    protected ImageFloat filteredDepthImage;

    /**
     * Integrate configuration
     */
    protected boolean firstPass;
    protected Matrix4x4Float invTrack;

    /**
     * FPS
     */
    protected long frames;
    /**
     * bilateral filter
     */
    protected FloatArray gaussian;
    protected final Float3 integrateDelta;
    protected int integrationCount;

    protected int integrationRate;
    protected int renderingRate;

    /**
     * camera config
     */
    protected final Matrix4x4Float invK;
    protected final Matrix4x4Float K;
    protected float largeStep;

    protected final Float3 light;
    protected float maxWeight;
    /**
     * Raycast configuration (render model)
     */
    protected final Matrix4x4Float modelPose;

    protected float mu;

    /**
     * Raycast configuration
     */
    protected float nearPlane;

    protected float normalThreshold;
    protected Matrix4x4Float preTrans;
    protected final Matrix4x4Float projectReference;

    /**
     * pose estimation
     */
    protected ImageFloat[] pyramidDepths;
    protected int[] pyramidIterations;

    protected ImageFloat3[] pyramidNormals;

    protected ImageFloat8[] pyramidTrackingResults;
    protected ImageFloat3[] pyramidVerticies;

    protected int radius;
    /**
     * Reference view
     */
    protected View referenceView;
    protected ImageByte4 renderedCurrentViewImage;
    protected ImageByte4 renderedDepthImage;
    protected ImageByte4 renderedReferenceViewImage;

    protected ImageByte3 renderedTrackingImage;
    /**
     * Render buffers
     */
    protected int renderScale;
    protected Matrix4x4Float rot;
    /**
     * Tracking parameters
     */
    protected float RSMEThreshold;

    /**
     * input scaling
     */
    protected ImageFloat scaledDepthImage;

    protected final Matrix4x4Float scaledInvK;
    protected ImageByte3 scaledVideoImage;
    protected int scalingFactor;

    protected float smallStep;
    protected long statsRate;
    protected final Float3 tmp;

    protected TrackingResult trackingResult;
    protected float trackingThreshold;
    protected Matrix4x4Float trans;
    /**
     * video inputs
     */
    protected VideoCamera videoCamera;

    protected ImageByte3 videoImageInput;
    /**
     * scene model
     */
    protected VolumeShort2 volume;
    protected final Float3 volumeDims;
    protected final Int3 volumeSize;
    protected final Matrix4x4Float referencePose;
    protected final Matrix4x4Float invReferencePose;

    protected ImageByte4 renderedScene;
    protected View sceneView;
    protected ImageFloat sceneDepths;

    // used to replace Float6.scale
    private static FloatArray scale(FloatArray array, float value) {
        final FloatArray result = new FloatArray(6);
        for (int i = 0; i < result.getSize(); i++) {
            result.set(i, array.get(i) * value);
        }
        return result;
    }

    protected AbstractPipeline(T config) {
        this.config = config;

        inputSize = new Int2();
        scaledInputSize = new Int2();

        /*
         * static camera parameters
         */
        camera = new Float4();
        scaledCamera = new Float4();
        K = new Matrix4x4Float();
        invK = new Matrix4x4Float();
        scaledInvK = new Matrix4x4Float();

        /*
         * static volume parameters
         */
        volumeSize = new Int3();
        volumeDims = new Float3();
        referencePose = new Matrix4x4Float();
        invReferencePose = new Matrix4x4Float();
        /*
         * static rendering parameters
         */
        modelPose = new Matrix4x4Float();
        light = new Float3();
        ambient = new Float3();

        /*
         * misc parameters
         */
        invTrack = new Matrix4x4Float();
        projectReference = new Matrix4x4Float();
        integrateDelta = new Float3();
        tmp = new Float3();
        cameraDelta = new Float3();

        statsRate = Long.MAX_VALUE;
    }

    public void configure(final Device device) {

        /*
         * configure devices
         */
        info("Setting depth camera...");
        config.setDepthInput(device);

        info("Setting video camera...");
        config.setVideoInput(device);

        /*
         * configure depth input
         */
        if (config.getDepthInput() != null) {
            depthCamera = config.getDepthInput();
            depthImageInput = new ImageFloat(depthCamera.getWidth(), depthCamera.getHeight());
            info("depth input image: {%d, %d}\n", depthImageInput.X(), depthImageInput.Y());
        } else {
            warn("No depth input available: disabling depth");
        }

        /*
         * configure video input
         */
        if (config.getVideoInput() != null) {
            videoCamera = config.getVideoInput();
            videoImageInput = new ImageByte3(videoCamera.getWidth(), videoCamera.getHeight());
            info("video input image: {%d, %d}\n", videoImageInput.X(), videoImageInput.Y());
        } else {
            warn("No video input available: disabling video");
        }

        /*
         * configure scaling and filtering
         */
        info("depth camera: %s", depthCamera.toString());
        inputSize.setX(depthCamera.getWidth());
        inputSize.setY(depthCamera.getHeight());

        scalingFactor = config.getScale();

        scaledInputSize.set(Int2.div(inputSize, scalingFactor));

        depthScaleFactor = config.getDepthScaleFactor();
        eDelta = config.getE_delta();
        radius = config.getRadius();
        delta = config.getDelta();

        gaussian = new FloatArray((radius * 2) + 1);
        generateGaussian();

        filteredDepthImage = new ImageFloat(scaledInputSize.getX(), scaledInputSize.getY());
        info("filtered depth image: {%d, %d}", filteredDepthImage.X(), filteredDepthImage.Y());

        /**
         * configure reference view
         */
        final Matrix4x4Float referenceViewPose = new Matrix4x4Float();

        final ImageFloat3 referenceViewVerticies = new ImageFloat3(scaledInputSize.getX(), scaledInputSize.getY());
        info("ref view verticies: {%d, %d}", referenceViewVerticies.X(), referenceViewVerticies.Y());

        final ImageFloat3 referenceViewNormals = new ImageFloat3(scaledInputSize.getX(), scaledInputSize.getY());
        info("ref view normals  : {%d, %d}", referenceViewNormals.X(), referenceViewNormals.Y());

        referenceView = new View(referenceViewNormals, referenceViewVerticies, referenceViewPose);

        /**
         * configure current view
         */
        final ImageFloat3 currentViewNormals = new ImageFloat3(scaledInputSize.getX(), scaledInputSize.getY());
        info("current view normals image: {%d, %d}", currentViewNormals.X(), currentViewNormals.Y());

        final ImageFloat3 currentViewVerticies = new ImageFloat3(scaledInputSize.getX(), scaledInputSize.getY());
        info("current view verticies image: {%d, %d}", currentViewVerticies.X(), currentViewVerticies.Y());

        final Matrix4x4Float currentViewPose = new Matrix4x4Float();
        info("intial pose:\t%s", config.getInitialPose());

        currentViewPose.set(FloatSE3.toMatrix4(config.getInitialPose()));
        info("current view pose:\t%s", currentViewPose.toString());

        currentView = new View(currentViewNormals, currentViewVerticies, currentViewPose);

        /**
         * configure camera
         */
        // do I need to scale this??
        camera.set(config.getCamera().duplicate());
        info("camera: %s", camera.toString());

        scaledCamera.set(Helper.scaleCameraConfig(config.getCamera().duplicate(), config.getScale()));
        info("scaled camera: %s", scaledCamera.toString());

        GraphicsMath.getInverseCameraMatrix(camera, invK);

        GraphicsMath.getInverseCameraMatrix(scaledCamera, scaledInvK);

        /**
         * configure scene model
         */
        volumeSize.set(config.getVolumeSize());

        volume = new VolumeShort2(volumeSize.getX(), volumeSize.getY(), volumeSize.getZ());

        initVolume(new Float2(1f, 0f));

        volumeDims.set(config.getVolumeDimensions());
        info("volume dims: {%f, %f, %f}", volumeDims.getX(), volumeDims.getY(), volumeDims.getZ());

        info("voxel size: {%f, %f, %f} in meters", volumeDims.getX() / volume.X(), volumeDims.getY() / volume.Y(), volumeDims.getZ() / volume.Z());

        /**
         * configure input scaling of depth and video images
         */
        scaledDepthImage = new ImageFloat(scaledInputSize.getX(), scaledInputSize.getY());
        info("scaled depth image: {%d, %d}", scaledDepthImage.X(), scaledDepthImage.Y());

        scaledVideoImage = new ImageByte3(scaledInputSize.getX(), scaledInputSize.getY());
        info("scaled video image: {%d, %d}", scaledVideoImage.X(), scaledVideoImage.Y());

        /**
         * configure pose estimation
         */
        pyramidIterations = config.getIterations();

        GraphicsMath.getCameraMatrix(scaledCamera, K);

        pyramidDepths = new ImageFloat[pyramidIterations.length];
        pyramidVerticies = new ImageFloat3[pyramidIterations.length];
        pyramidNormals = new ImageFloat3[pyramidIterations.length];
        pyramidTrackingResults = new ImageFloat8[pyramidIterations.length];

        info("image pyramid: number of levels = %d", pyramidIterations.length);
        for (int i = 0; i < pyramidIterations.length; i++) {
            final int x = scaledInputSize.getX() >>> i;
            final int y = scaledInputSize.getY() >>> i;
            info("image pyramid: level=%d, {%d,%d}", i, x, y);
            pyramidDepths[i] = new ImageFloat(x, y);
            pyramidVerticies[i] = new ImageFloat3(x, y);
            pyramidNormals[i] = new ImageFloat3(x, y);
            pyramidTrackingResults[i] = new ImageFloat8(x, y);
        }

        /**
         * configure ICP
         */
        distanceThreshold = config.getDistanceThreshold();
        normalThreshold = config.getNormalThreshold();

        /**
         * configure tracking parameters
         */
        RSMEThreshold = (float) config.getRSMEThreshold();
        trackingThreshold = config.getTrackingThreshold();

        trackingResult = new TrackingResult();

        /**
         * configure integration
         */
        firstPass = true;
        integrationCount = 0;
        integrationRate = config.getIntegrationRate();
        renderingRate = config.getRenderingRate();

        mu = config.getMu();
        maxWeight = config.getMaxweight();

        /**
         * configure raycast (update reference view)
         */
        nearPlane = config.getNearPlane();
        farPlane = config.getFarPlane();
        largeStep = 0.75f * config.getMu();
        smallStep = Float3.min(volumeDims) / Int3.max(volumeSize);

        /**
         * configure raycast (render model)
         */
        FloatArray float6 = new FloatArray(0, 0, -volumeDims.getX(), 0, 0, 0);

        preTrans = new FloatSE3(float6).toMatrix4();
        FloatArray value = new FloatArray(.5f, .5f, .5f, 0, 0, 0);

        value = scale(value, volumeDims.getX());

        trans = new FloatSE3(value).toMatrix4();

        FloatArray aux = new FloatArray(6);
        rot = new FloatSE3(aux).toMatrix4();

        renderedScene = new ImageByte4(inputSize.getX(), inputSize.getY());

        info("scene image: {%d, %d}", renderedScene.X(), renderedScene.Y());

        final ImageFloat3 verticies = new ImageFloat3(inputSize.getX(), inputSize.getY());
        info("scene verticies: {%d, %d}", verticies.X(), verticies.Y());

        final ImageFloat3 normals = new ImageFloat3(inputSize.getX(), inputSize.getY());
        info("scene normals  : {%d, %d}", normals.X(), normals.Y());

        sceneDepths = new ImageFloat(inputSize.getX(), inputSize.getY());
        info("scene depth  : {%d, %d}", sceneDepths.X(), sceneDepths.Y());

        final Matrix4x4Float view = new FloatSE3(config.getInitialPose()).toMatrix4();

        info("scene pose: \n%s", view.toString());

        sceneView = new View(normals, verticies, view);

        /**
         * configure render buffers
         */
        renderScale = config.getScale() - 1;
        light.set(config.getLight());
        ambient.set(config.getAmbient());

        renderedDepthImage = new ImageByte4(scaledInputSize.getX(), scaledInputSize.getY());
        info("rendered depth image: {%d, %d}", renderedDepthImage.X(), renderedDepthImage.Y());

        renderedCurrentViewImage = new ImageByte4(scaledInputSize.getX(), scaledInputSize.getY());
        info("rendered current view image: {%d, %d}", renderedCurrentViewImage.X(), renderedCurrentViewImage.Y());

        renderedReferenceViewImage = new ImageByte4(scaledInputSize.getX(), scaledInputSize.getY());
        info("rendered ref view image: {%d, %d}", renderedReferenceViewImage.X(), renderedReferenceViewImage.Y());

        renderedTrackingImage = new ImageByte3(scaledInputSize.getX(), scaledInputSize.getY());

        info("rendered tracking image: {%d, %d}", renderedTrackingImage.X(), renderedTrackingImage.Y());

        /**
         * FPS
         */
        frames = 0;
        statsRate = 30;
        accumulatedTime = 0;

    }

    public abstract void execute();

    protected void preprocessing() {
        ImagingOps.mm2metersKernel(scaledDepthImage, depthImageInput, scalingFactor);
        ImagingOps.bilateralFilter(filteredDepthImage, scaledDepthImage, gaussian, eDelta, radius);
    }

    protected boolean estimatePose() {
        if (config.debug()) {
            info("============== estimaing pose ==============");
        }

        // populate first row of the pyramid with the current (measured)
        // view
        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();

        // populate remaining layers of the image pyramid
        for (int i = 1; i < pyramidIterations.length; i++) {
            ImagingOps.resizeImage6(pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 1);
        }

        final Matrix4x4Float scaledInvKDup = new Matrix4x4Float();
        for (int i = 0; i < pyramidIterations.length; i++) {
            if (config.debug()) {
                info("level: %d", i);
            }

            final Float4 cameraDup = Float4.mult(scaledCamera, 1f / (float) (1 << i));
            GraphicsMath.getInverseCameraMatrix(cameraDup, scaledInvKDup);

            if (config.debug()) {
                info("scaled camera: %s", cameraDup.toString());
                info("scaled InvK:\n%s", scaledInvKDup.toString());
                info(String.format("size: {%d,%d}", pyramidVerticies[i].X(), pyramidVerticies[i].Y()));
            }

            GraphicsMath.depth2vertex(pyramidVerticies[i], pyramidDepths[i], scaledInvKDup);
            GraphicsMath.vertex2normal(pyramidNormals[i], pyramidVerticies[i]);
        }

        invReferencePose.set(referenceView.getPose());
        MatrixFloatOps.inverse(invReferencePose);

        MatrixMath.sgemm(K, invReferencePose, projectReference);

        if (config.debug()) {
            info("camera matrix    :\n%s", K.toString());
            info("inv ref pose     :\n%s", invReferencePose.toString());
            info("project reference:\n%s", projectReference.toString());
        }

        // perform ICP
        final Matrix4x4Float pose = currentView.getPose().duplicate();
        for (int level = pyramidIterations.length - 1; level >= 0; level--) {
            for (int i = 0; i < pyramidIterations[level]; i++) {
                if (config.debug()) {
                    info("-------------start iteration----------------");
                    info(String.format("level: %d", level));
                    info(String.format("iteration: %d", i));
                    info(String.format("size: {%d,%d}", pyramidVerticies[level].X(), pyramidVerticies[level].Y()));
                    info(String.format("verticies: " + pyramidVerticies[level].summarise()));
                    info(String.format("normals: " + pyramidNormals[level].summarise()));
                    info(String.format("ref verticies: " + referenceView.getVerticies().summarise()));
                    info(String.format("ref normals: " + referenceView.getNormals().summarise()));
                    info(String.format("pose: \n%s\n", pose.toString(FloatOps.FMT_4_EM)));
                }

                IterativeClosestPoint.trackPose(pyramidTrackingResults[level], pyramidVerticies[level], pyramidNormals[level], referenceView.getVerticies(), referenceView.getNormals(), pose,
                        projectReference, distanceThreshold, normalThreshold);

                boolean updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, pyramidTrackingResults[level], pose, 1e-5f);

                if (config.debug()) {
                    info("solve: %s", trackingResult.toString());
                }

                if (config.debug()) {
                    info("solve: %s", trackingResult.toString());
                }

                pose.set(trackingResult.getPose());

                if (config.debug()) {
                    info("estimated pose:\n%s", trackingResult.toString());
                    info("-------------end iteration----------------");
                }
                if (updated) {
                    break;
                }

            }
        }

        // if the tracking result meets our constraints, update the current view with
        // the estimated
        // pose
        boolean hasTracked = trackingResult.getRSME() < RSMEThreshold && trackingResult.getTracked(scaledInputSize.getX() * scaledInputSize.getY()) >= trackingThreshold;
        if (hasTracked) {
            currentView.getPose().set(trackingResult.getPose());
        }

        // info("at integrate: hasTracked=%s || firstPass=%s", hasTracked, firstPass);
        return hasTracked;
    }

    protected void integrate() {
        invTrack.set(currentView.getPose());
        MatrixFloatOps.inverse(invTrack);

        if (config.debug()) {
            info("============== integration ==============");
            info("invTrack:\n%s\n", invTrack.toString(FloatOps.FMT_4_EM));
            info("K:\n%s\n", K.toString(FloatOps.FMT_4_EM));
        }

        kfusion.java.algorithms.Integration.integrate(scaledDepthImage, invTrack, K, volumeDims, volume, mu, maxWeight);
    }

    public void updateReferenceView() {

        if (config.debug()) {
            info("============== updating reference view ==============");
        }

        final ImageFloat3 verticies = referenceView.getVerticies();
        final ImageFloat3 normals = referenceView.getNormals();

        referenceView.getPose().set(currentView.getPose());

        // convert the tracked pose into correct co-ordinate system for
        // raycasting
        // which system (homogeneous co-ordinates? or virtual image?)
        MatrixMath.sgemm(currentView.getPose(), scaledInvK, referencePose);
        if (config.debug()) {
            info("current   pose: %s", currentView.getPose().toString());
            info("reference pose: %s", referencePose.toString());
        }

        Raycast.raycast(verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep, smallStep);
    }

    public void updateUserPose() {
        final Matrix4x4Float tmp = new Matrix4x4Float();
        final Matrix4x4Float tmp2 = new Matrix4x4Float();

        if (config.getAndClearRotateNegativeX()) {
            updateRotation(rot, config.getUptrans());
        }

        if (config.getAndClearRotatePositiveX()) {
            updateRotation(rot, config.getDowntrans());
        }

        if (config.getAndClearRotatePositiveY()) {
            updateRotation(rot, config.getRighttrans());
        }

        if (config.getAndClearRotateNegativeY()) {
            updateRotation(rot, config.getLefttrans());
        }

        MatrixMath.sgemm(trans, rot, tmp);
        MatrixMath.sgemm(tmp, preTrans, tmp2);
        MatrixMath.sgemm(tmp2, scaledInvK, modelPose);
    }

    public void renderScene() {
        final Matrix4x4Float scenePose = sceneView.getPose();
        Renderer.renderVolume(renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep, largeStep, light, ambient);
    }

    private void generateGaussian() {
        for (int i = 0; i < gaussian.getSize(); i++) {
            final int x = i - radius;
            gaussian.set(i, (float) Math.exp(-(x * x) / (2 * delta * delta)));
        }
    }

    // TODO simplify this
    private void initVolume(final Float2 val) {
        final Short2 tmp = new Short2();
        tmp.setX((short) (val.getX() * 32766.0f));
        tmp.setY((short) val.getY());

        for (int z = 0; z < volume.Z(); z++) {
            for (int y = 0; y < volume.Y(); y++) {
                for (int x = 0; x < volume.X(); x++) {
                    volume.set(x, y, z, tmp);
                }
            }
        }

    }

    public boolean isValid() {
        boolean result = true;

        result = (depthCamera != null) ? true : false;
        result = (videoCamera != null) ? true : false;

        return result;
    }

    public void quit() {
        info("Shutting down kfusion pipeline");
        final Device device = config.getDevice();
        if (device.isRunning()) {
            info("Stopping device");
            device.stop();
        }
    }

    public void reset() {
        info("Resetting kfusion pipeline");
        final Device device = config.getDevice();
        if (device.isRunning()) {
            info("Stopping device");
            device.stop();
        }

        configure(device);

        if (isValid()) {
            info("Starting device");
            device.start();
        } else {
            fatal("configuration is not valid - unable to start device");
        }
    }

    public long getProcessedFrames() {
        return frames;
    }

}
