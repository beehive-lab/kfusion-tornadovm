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
package kfusion.pipeline;

import kfusion.KfusionConfig;
import uk.ac.manchester.tornado.api.collections.graphics.ImagingOps;
import uk.ac.manchester.tornado.api.collections.graphics.Renderer;

public class BilateralFilterPipeline<T extends KfusionConfig> extends AbstractOpenGLPipeline<T> {

    public BilateralFilterPipeline(T config) {
        super(config);
        config.setDrawDepth(true);
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
