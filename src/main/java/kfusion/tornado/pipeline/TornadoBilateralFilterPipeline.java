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
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.collections.graphics.ImagingOps;
import uk.ac.manchester.tornado.api.collections.graphics.Renderer;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

public class TornadoBilateralFilterPipeline<T extends TornadoModel> extends AbstractOpenGLPipeline<T> {

    public TornadoBilateralFilterPipeline(T config) {
        super(config);
        config.setDrawDepth(true);
    }

    @Override
    protected void preprocessing() {
        super.preprocessing();
        preprocessingPlan.execute();
    }

    private TornadoDevice oclDevice;
    private TaskGraph preprocessingGraph;
    private TornadoExecutionPlan preprocessingPlan;

    @Override
    public void configure(Device device) {
        super.configure(device);

        oclDevice = config.getTornadoDevice();
        info("mapping onto %s\n", oclDevice.toString());

        /*
         * Cleanup after previous configurations
         */
        oclDevice.reset();


        preprocessingGraph = new TaskGraph("pp")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, depthImageInput)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, scaledDepthImage, filteredDepthImage, scaledDepthImage, gaussian)
                .task("mm2meters", ImagingOps::mm2metersKernel, scaledDepthImage, depthImageInput, scalingFactor)
                .task("bilateralFilter", ImagingOps::bilateralFilter, filteredDepthImage, scaledDepthImage, gaussian, eDelta, radius)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, filteredDepthImage);
        preprocessingPlan = new TornadoExecutionPlan(preprocessingGraph.snapshot()).withDevice(oclDevice);
        preprocessingPlan.withWarmUp();

    }

    @Override
    public void execute() {
        boolean haveDepthImage = depthCamera.pollDepth(depthImageInput);
        boolean haveVideoImage = videoCamera.pollVideo(videoImageInput);

        if (haveDepthImage) {
            preprocessing();

            Renderer.renderDepth(renderedDepthImage, filteredDepthImage, nearPlane, farPlane);
        }

        if (haveVideoImage) {
            ImagingOps.resizeImage(scaledVideoImage, videoImageInput, scalingFactor);
        }

    }
}
