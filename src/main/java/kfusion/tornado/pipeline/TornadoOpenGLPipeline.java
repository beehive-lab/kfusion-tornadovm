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

import kfusion.java.devices.Device;
import kfusion.java.pipeline.AbstractOpenGLPipeline;
import kfusion.tornado.algorithms.GraphicsMath;
import kfusion.tornado.algorithms.ImagingOps;
import kfusion.tornado.algorithms.Integration;
import kfusion.tornado.algorithms.IterativeClosestPoint;
import kfusion.tornado.algorithms.Raycast;
import kfusion.tornado.algorithms.Renderer;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.matrix.MatrixFloatOps;
import uk.ac.manchester.tornado.matrix.MatrixMath;

import static uk.ac.manchester.tornado.matrix.MatrixMath.sgemm;

public class TornadoOpenGLPipeline<T extends TornadoModel> extends AbstractOpenGLPipeline<T> {

    public TornadoOpenGLPipeline(T config) {
        super(config);
    }

    private TornadoDevice acceleratorDevice;

    /**
     * Tornado
     */
    private TaskGraph preprocessingGraph;
    private TaskGraph estimatePoseGraph;
    private TaskGraph trackingPyramidGraphs[];
    private TaskGraph integrateGraph;
    private TaskGraph raycastGraph;
    private TaskGraph renderGraph;

    private TornadoExecutionPlan preprocessingPlan;
    private TornadoExecutionPlan estimatePosePlan;
    private TornadoExecutionPlan trackingPyramidPlans[];
    private TornadoExecutionPlan integratePlan;
    private TornadoExecutionPlan raycastPlan;
    private TornadoExecutionPlan renderPlan;

    private Matrix4x4Float pyramidPose;
    private Matrix4x4Float[] scaledInvKs;

    private FloatArray icpResultIntermediate1;
    private FloatArray icpResult;

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
        acceleratorDevice.clean();

        pyramidPose = new Matrix4x4Float();
        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();

        icpResultIntermediate1 = new FloatArray(config.getReductionSize() * 32);
        icpResult = new FloatArray(32);

        final Matrix4x4Float scenePose = sceneView.getPose();

        preprocessingGraph = new TaskGraph("pp")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, depthImageInput)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, scaledDepthImage, scalingFactor, gaussian)
                .task("mm2meters", ImagingOps::mm2metersKernel, scaledDepthImage, depthImageInput, scalingFactor)
                .task("bilateralFilter", ImagingOps::bilateralFilter, pyramidDepths[0], scaledDepthImage, gaussian, eDelta, radius);

        preprocessingPlan = new TornadoExecutionPlan(preprocessingGraph.snapshot()).withDevice(acceleratorDevice);

        final int iterations = pyramidIterations.length;
        scaledInvKs = new Matrix4x4Float[iterations];
        for (int i = 0; i < iterations; i++) {
            final Float4 cameraDup = Float4.mult(scaledCamera, 1f / (1 << i));
            scaledInvKs[i] = new Matrix4x4Float();
            GraphicsMath.getInverseCameraMatrix(cameraDup, scaledInvKs[i]);
        }

        estimatePoseGraph = new TaskGraph("estimatePose");

        for (int i = 1; i < iterations; i++) {
            estimatePoseGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, projectReference, pyramidDepths[i], pyramidVerticies[i], scaledInvKs[i], pyramidNormals[i]);
            estimatePoseGraph.task("resizeImage" + i, ImagingOps::resizeImage6, pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2);
        }

        for (int i = 0; i < iterations; i++) {
            estimatePoseGraph
                    .task("d2v" + i, GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i])
                    .task("v2n" + i, GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]);
        }

        estimatePoseGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, projectReference);

        estimatePosePlan = new TornadoExecutionPlan(estimatePoseGraph.snapshot()).withDevice(acceleratorDevice);

        trackingPyramidGraphs = new TaskGraph[iterations];
        trackingPyramidPlans = new TornadoExecutionPlan[trackingPyramidGraphs.length];
        for (int i = 0; i < iterations; i++) {

            trackingPyramidGraphs[i] = new TaskGraph("icp" + i)
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, pyramidPose)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                            referenceView.getVerticies(), referenceView.getNormals(), projectReference)
                    .task("track" + i, IterativeClosestPoint::trackPose,
                            pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                            referenceView.getVerticies(), referenceView.getNormals(), pyramidPose,
                            projectReference, distanceThreshold, normalThreshold)
                    .task("mapreduce" + i, IterativeClosestPoint::mapReduce, icpResultIntermediate1, pyramidTrackingResults[i])
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, icpResultIntermediate1);

            trackingPyramidPlans[i] = new TornadoExecutionPlan(trackingPyramidGraphs[i].snapshot()).withDevice(acceleratorDevice);
        }

        integrateGraph = new TaskGraph("integrate")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, invTrack)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, K, volumeDims, volume)
                .task("integrate", Integration::integrate, scaledDepthImage, invTrack, K, volumeDims, volume, mu, maxWeight);

        integratePlan = new TornadoExecutionPlan(integrateGraph.snapshot()).withDevice(acceleratorDevice);

        final ImageFloat3 verticies = referenceView.getVerticies();
        final ImageFloat3 normals = referenceView.getNormals();

        raycastGraph = new TaskGraph("raycast")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, referencePose)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, volume, volumeDims)
                .task("raycast", Raycast::raycast, verticies, normals, volume, volumeDims, referencePose, nearPlane, farPlane, largeStep, smallStep);

        raycastPlan = new TornadoExecutionPlan(raycastGraph.snapshot()).withDevice(acceleratorDevice);

        renderGraph = new TaskGraph("render")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scenePose)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, renderedScene, volume, volumeDims, light, ambient, pyramidVerticies[0], pyramidNormals[0], verticies, normals, pyramidTrackingResults[0])
                .task("renderCurrentView", Renderer::renderLight, renderedCurrentViewImage, pyramidVerticies[0], pyramidNormals[0], light, ambient)
                .task("renderReferenceView", Renderer::renderLight, renderedReferenceViewImage, verticies, normals, light, ambient)
                .task("renderTrack", Renderer::renderTrack, renderedTrackingImage, pyramidTrackingResults[0])
                .task("renderVolume", Renderer::renderVolume, renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep, largeStep, light, ambient)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, renderedCurrentViewImage, renderedReferenceViewImage, renderedTrackingImage, renderedDepthImage, renderedScene);

        renderPlan = new TornadoExecutionPlan(renderGraph.snapshot()).withDevice(acceleratorDevice);

        preprocessingPlan.withPreCompilation();
        estimatePosePlan.withPreCompilation();
        for (TornadoExecutionPlan trackingPyramid1 : trackingPyramidPlans) {
            trackingPyramid1.withPreCompilation();
        }
        integratePlan.withPreCompilation();
        raycastPlan.withPreCompilation();
        renderPlan.withPreCompilation();
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

        estimatePosePlan.execute();

        // perform ICP
        pyramidPose.set(currentView.getPose());
        for (int level = pyramidIterations.length - 1; level >= 0; level--) {
            for (int i = 0; i < pyramidIterations[level]; i++) {
                trackingPyramidPlans[level].execute();
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

            renderPlan.execute();
        }
    }

    @Override
    protected void integrate() {
        invTrack.set(currentView.getPose());
        MatrixFloatOps.inverse(invTrack);

        integratePlan.execute();

    }

    @Override
    protected void preprocessing() {
        preprocessingPlan.execute();
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

        raycastPlan.execute();
    }
}
