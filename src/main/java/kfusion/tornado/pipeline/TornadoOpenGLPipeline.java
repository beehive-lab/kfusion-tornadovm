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
package kfusion.tornado.pipeline;

import static uk.ac.manchester.tornado.matrix.MatrixMath.sgemm;

import kfusion.java.devices.Device;
import kfusion.java.pipeline.AbstractOpenGLPipeline;
import kfusion.tornado.algorithms.Integration;
import kfusion.tornado.algorithms.IterativeClosestPoint;
import kfusion.tornado.algorithms.Raycast;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.graphics.ImagingOps;
import uk.ac.manchester.tornado.api.collections.graphics.Renderer;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.matrix.MatrixFloatOps;
import uk.ac.manchester.tornado.matrix.MatrixMath;

public class TornadoOpenGLPipeline<T extends TornadoModel> extends AbstractOpenGLPipeline<T> {

    public TornadoOpenGLPipeline(T config) {
        super(config);
    }

    private TornadoDevice acceleratorDevice;

    /**
     * Tornado
     */
    private TaskGraph preprocessingSchedule;
    private TaskGraph estimatePoseSchedule;
    private TaskGraph trackingPyramid[];
    private TaskGraph integrateSchedule;
    private TaskGraph raycastSchedule;
    private TaskGraph renderSchedule;

    private Matrix4x4Float pyramidPose;
    private Matrix4x4Float[] scaledInvKs;

    private float[] icpResultIntermediate1;
    private float[] icpResult;

    @Override
    public void configure(final Device device) {
        super.configure(device);

        /**
         * Tornado tasks
         */
        acceleratorDevice = config.getTornadoDevice();
        info("mapping onto %s\n", acceleratorDevice.toString());

        /*
         * Cleanup after previous configurations
         */
        acceleratorDevice.reset();

        pyramidPose = new Matrix4x4Float();
        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();

        icpResultIntermediate1 = new float[config.getReductionSize() * 32];
        icpResult = new float[32];

        final Matrix4x4Float scenePose = sceneView.getPose();

        //@formatter:off
        preprocessingSchedule = new TaskGraph("pp")
                .lockObjectsInMemory(depthImageInput, scaledDepthImage, pyramidDepths[0], gaussian)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, depthImageInput)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, scaledDepthImage, scalingFactor, gaussian)
                .task("mm2meters", ImagingOps::mm2metersKernel, scaledDepthImage, depthImageInput, scalingFactor)
                .task("bilateralFilter", ImagingOps::bilateralFilter, pyramidDepths[0], scaledDepthImage, gaussian, eDelta, radius)
                .mapAllTo(acceleratorDevice);
        //@formatter:on

        final int iterations = pyramidIterations.length;
        scaledInvKs = new Matrix4x4Float[iterations];
        for (int i = 0; i < iterations; i++) {
            final Float4 cameraDup = Float4.mult(scaledCamera, 1f / (1 << i));
            scaledInvKs[i] = new Matrix4x4Float();
            GraphicsMath.getInverseCameraMatrix(cameraDup, scaledInvKs[i]);
        }

        estimatePoseSchedule = new TaskGraph("estimatePose");

        for (int i = 1; i < iterations; i++) {
            estimatePoseSchedule.transferToDevice(DataTransferMode.FIRST_EXECUTION, projectReference, pyramidDepths[i], pyramidVerticies[i], scaledInvKs[i], pyramidNormals[i]);
            //@formatter:off
            estimatePoseSchedule
                    .task("resizeImage" + i, ImagingOps::resizeImage6, pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2);
            //@formatter:on
        }

        for (int i = 0; i < iterations; i++) {
            //@formatter:off
            estimatePoseSchedule
                    .task("d2v" + i, GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i])
                    .task("v2n" + i, GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]);
            //@formatter:on
        }

        estimatePoseSchedule.transferToDevice(DataTransferMode.EVERY_EXECUTION, projectReference).mapAllTo(acceleratorDevice);

        for (int i = 0; i < iterations; i++) {
            estimatePoseSchedule.lockObjectsInMemory(projectReference, pyramidDepths[i], pyramidVerticies[i], scaledInvKs[i], pyramidNormals[i]);
        }

        trackingPyramid = new TaskGraph[iterations];
        for (int i = 0; i < iterations; i++) {

            //@formatter:off
            trackingPyramid[i] = new TaskGraph("icp" + i)
                    .lockObjectsInMemory(pyramidPose, pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                            referenceView.getVerticies(), referenceView.getNormals(), projectReference)
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, pyramidPose)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                            referenceView.getVerticies(), referenceView.getNormals(), projectReference)
                    .task("track" + i, IterativeClosestPoint::trackPose,
                            pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                            referenceView.getVerticies(), referenceView.getNormals(), pyramidPose,
                            projectReference, distanceThreshold, normalThreshold)
                    .task("mapreduce" + i, IterativeClosestPoint::mapReduce, icpResultIntermediate1, pyramidTrackingResults[i])
                    .transferToHost(icpResultIntermediate1)
                    .mapAllTo(acceleratorDevice);
            //@formatter:on
        }

        //@formatter:off
        integrateSchedule = new TaskGraph("integrate")
                .lockObjectsInMemory(scaledDepthImage, invTrack, K, volumeDims, volume)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, invTrack)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, K, volumeDims, volume)
                .task("integrate", Integration::integrate, scaledDepthImage, invTrack, K, volumeDims, volume, mu, maxWeight)
                .mapAllTo(acceleratorDevice);
        //@formatter:on

        final ImageFloat3 verticies = referenceView.getVerticies();
        final ImageFloat3 normals = referenceView.getNormals();

        //@formatter:off
        raycastSchedule = new TaskGraph("raycast")
                .lockObjectsInMemory(referencePose, verticies, normals, volume, volumeDims, referencePose)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, referencePose)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, volume, volumeDims)
                .task("raycast", Raycast::raycast, verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep, smallStep)
                .mapAllTo(acceleratorDevice);
        //@formatter:on

        renderSchedule = new TaskGraph("render")
                .lockObjectsInMemory(renderedScene, volume, volumeDims, scenePose, light, ambient, pyramidVerticies[0], pyramidNormals[0], verticies, normals, pyramidTrackingResults[0])
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scenePose)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, renderedScene, volume, volumeDims, light, ambient, pyramidVerticies[0], pyramidNormals[0], verticies, normals, pyramidTrackingResults[0])
                .task("renderCurrentView", Renderer::renderLight, renderedCurrentViewImage, pyramidVerticies[0], pyramidNormals[0], light, ambient)
                .task("renderReferenceView", Renderer::renderLight, renderedReferenceViewImage, verticies, normals, light, ambient)
                .task("renderTrack", Renderer::renderTrack, renderedTrackingImage, pyramidTrackingResults[0])
                .task("renderVolume", Renderer::renderVolume, renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep, largeStep, light, ambient)
                .transferToHost(renderedCurrentViewImage, renderedReferenceViewImage, renderedTrackingImage, renderedDepthImage, renderedScene)
                .mapAllTo(acceleratorDevice);
        //@formatter:on

        preprocessingSchedule.warmup();
        estimatePoseSchedule.warmup();
        for (TaskGraph trackingPyramid1 : trackingPyramid) {
            trackingPyramid1.warmup();
        }
        integrateSchedule.warmup();
        raycastSchedule.warmup();
        renderSchedule.warmup();
    }

    @Override
    protected boolean estimatePose() {
        if (config.debug()) {
            info("============== estimating pose ==============");
        }

        invReferencePose.set(referenceView.getPose());
        MatrixFloatOps.inverse(invReferencePose);

        sgemm(K, invReferencePose, projectReference);

        if (config.debug()) {
            info("camera matrix    :\n%s", K.toString());
            info("inv ref pose     :\n%s", invReferencePose.toString());
            info("project reference:\n%s", projectReference.toString());
        }

        estimatePoseSchedule.execute();

        // perform ICP
        pyramidPose.set(currentView.getPose());
        for (int level = pyramidIterations.length - 1; level >= 0; level--) {
            for (int i = 0; i < pyramidIterations[level]; i++) {
                trackingPyramid[level].execute();
                IterativeClosestPoint.reduceIntermediate(icpResult, icpResultIntermediate1);

                trackingResult.resultImage = pyramidTrackingResults[level];
                final boolean updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResult, pyramidPose, 1e-5f);

                pyramidPose.set(trackingResult.getPose());

                if (updated) {
                    break;
                }

            }
        }

        // if the tracking result meets our constraints, update the current view
        // with
        // the estimated
        // pose
        final boolean hasTracked = trackingResult.getRSME() < RSMEThreshold && trackingResult.getTracked(scaledInputSize.getX() * scaledInputSize.getY()) >= trackingThreshold;
        if (hasTracked) {
            currentView.getPose().set(trackingResult.getPose());
        }
        return hasTracked;
    }

    @Override
    public void execute() {
        final boolean haveDepthImage = depthCamera.pollDepth(depthImageInput);
        final boolean haveVideoImage = videoCamera.pollVideo(videoImageInput);

        if (haveDepthImage) {
            preprocessing();
            final boolean hasTracked = estimatePose();
            if (hasTracked && frames % integrationRate == 0 || firstPass) {
                integrate();
                updateReferenceView();
                if (firstPass) {
                    firstPass = false;
                }
            }
        }

        if (haveVideoImage) {
            ImagingOps.resizeImage(scaledVideoImage, videoImageInput, scalingFactor);
        }

        if (frames % renderingRate == 0) {
            final Matrix4x4Float scenePose = sceneView.getPose();
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
            MatrixMath.sgemm(tmp2, invK, scenePose);

            renderSchedule.execute();
        }
    }

    @Override
    protected void integrate() {
        invTrack.set(currentView.getPose());
        MatrixFloatOps.inverse(invTrack);

        integrateSchedule.execute();

    }

    @Override
    protected void preprocessing() {
        preprocessingSchedule.execute();
    }

    @Override
    public void updateReferenceView() {
        if (config.debug()) {
            info("============== updating reference view ==============");
        }

        referenceView.getPose().set(currentView.getPose());

        // convert the tracked pose into correct co-ordinate system for
        // raycasting
        // which system (homogeneous co-ordinates? or virtual image?)
        MatrixMath.sgemm(currentView.getPose(), scaledInvK, referencePose);

        raycastSchedule.execute();
    }
}
