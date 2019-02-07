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
package kfusion.pipeline;

import kfusion.KfusionConfig;
import uk.ac.manchester.tornado.api.collections.graphics.ImagingOps;
import uk.ac.manchester.tornado.api.collections.graphics.Renderer;

public class JavaOpenGLPipeline<T extends KfusionConfig> extends AbstractOpenGLPipeline<T> {

    public JavaOpenGLPipeline(T config) {
        super(config);
    }

    public void execute() {
        boolean haveDepthImage = depthCamera.pollDepth(depthImageInput);
        boolean haveVideoImage = videoCamera.pollVideo(videoImageInput);

        if (haveDepthImage) {
            preprocessing();

            boolean hasTracked = estimatePose();

            if (hasTracked && frames % integrationRate == 0 || firstPass) {

                integrate();

                updateReferenceView();

                if (firstPass)
                    firstPass = false;
            }

            Renderer.renderDepth(renderedDepthImage, filteredDepthImage, nearPlane, farPlane);
            Renderer.renderLight(renderedCurrentViewImage, currentView.getVerticies(), currentView.getNormals(), light, ambient);
            Renderer.renderTrack(renderedTrackingImage, trackingResult.getResultImage());
            Renderer.renderLight(renderedReferenceViewImage, referenceView.getVerticies(), referenceView.getNormals(), light, ambient);

        }

        if (haveVideoImage) {
            ImagingOps.resizeImage(scaledVideoImage, videoImageInput, scalingFactor);
        }

        if (frames % renderingRate == 0) {
            renderScene();
        }

    }
}
