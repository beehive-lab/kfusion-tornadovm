/*
 * Copyright 2017 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kfusion.tornado.pipeline;

import kfusion.java.devices.Device;
import kfusion.java.pipeline.AbstractOpenGLPipeline;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.graphics.ImagingOps;
import uk.ac.manchester.tornado.api.collections.graphics.Renderer;
import uk.ac.manchester.tornado.api.common.TornadoDevice;

public class TornadoBilateralFilterPipeline<T extends TornadoModel> extends AbstractOpenGLPipeline<T> {

    public TornadoBilateralFilterPipeline(T config) {
        super(config);
        config.setDrawDepth(true);
    }

    @Override
    protected void preprocessing() {
        super.preprocessing();
        preprocessingSchedule.execute();
    }

    private TornadoDevice oclDevice;
    private TaskSchedule preprocessingSchedule;

    @Override
    public void configure(Device device) {
        super.configure(device);

        oclDevice = config.getTornadoDevice();
        info("mapping onto %s\n", oclDevice.toString());

        /*
         * Cleanup after previous configurations
         */
        oclDevice.reset();

        //@formatter:off
        preprocessingSchedule = new TaskSchedule("pp")
                .streamIn(depthImageInput)
                .task("mm2meters", ImagingOps::mm2metersKernel, scaledDepthImage, depthImageInput, scalingFactor)
                .task("bilateralFilter", ImagingOps::bilateralFilter, filteredDepthImage, scaledDepthImage, gaussian, eDelta, radius)
                .streamOut(filteredDepthImage)
                .mapAllTo(oclDevice);
        //@formatter:on

        preprocessingSchedule.warmup();

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
