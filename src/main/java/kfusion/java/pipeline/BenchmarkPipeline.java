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

import java.io.PrintStream;

import kfusion.java.common.KfusionConfig;
import kfusion.java.devices.Device;
import kfusion.tornado.algorithms.Renderer;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.utils.TornadoAPIUtils;

public class BenchmarkPipeline<T extends KfusionConfig> extends AbstractPipeline<T> {

    Float3 initialPosition;
    private final PrintStream out;

    public BenchmarkPipeline(T config, PrintStream out) {
        super(config);
        this.out = out;
        initialPosition = new Float3();
    }

    @Override
    public void execute() {
        if (config.getDevice() != null) {
            out.println("frame\tacquisition\tpreprocessing\ttracking\tintegration\traycasting\trendering\tcomputation\ttotal    \tX          \tY          \tZ         \ttracked   \tintegrated");

            final long[] timings = new long[7];

            timings[0] = System.nanoTime();
            boolean haveDepthImage = depthCamera.pollDepth(depthImageInput);
            videoCamera.skipVideoFrame();

            // read all frames
            while (haveDepthImage) {

                timings[1] = System.nanoTime();
                preprocessing();
                timings[2] = System.nanoTime();

                boolean hasTracked = estimatePose();

                timings[3] = System.nanoTime();

                final boolean doIntegrate = (hasTracked && frames % integrationRate == 0) || frames <= 3;
                if (doIntegrate) {
                    integrate();
                }

                timings[4] = System.nanoTime();

                final boolean doUpdate = frames > 2;

                if (doUpdate) {
                    updateReferenceView();
                }

                timings[5] = System.nanoTime();

                if (frames % renderingRate == 0) {

                    Renderer.renderTrack(renderedTrackingImage, trackingResult.getResultImage());

                    Renderer.renderDepth(renderedDepthImage, filteredDepthImage, nearPlane, farPlane);

                    final Matrix4x4Float scenePose = sceneView.getPose();

                    Renderer.renderVolume(renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep, largeStep, light, ambient);
                }

                timings[6] = System.nanoTime();
                final Float3 currentPos = currentView.getPose().column(3).asFloat3();
                final Float3 pos = Float3.sub(currentPos, initialPosition);

                out.printf("%d\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%d\t%d\n", frames, TornadoAPIUtils.elapsedTimeInSeconds(timings[0], timings[1]), TornadoAPIUtils.elapsedTimeInSeconds(timings[1], timings[2]),
                        TornadoAPIUtils.elapsedTimeInSeconds(timings[2], timings[3]), TornadoAPIUtils.elapsedTimeInSeconds(timings[3], timings[4]), TornadoAPIUtils.elapsedTimeInSeconds(timings[4], timings[5]),
                        TornadoAPIUtils.elapsedTimeInSeconds(timings[5], timings[6]), TornadoAPIUtils.elapsedTimeInSeconds(timings[1], timings[5]), TornadoAPIUtils.elapsedTimeInSeconds(timings[0], timings[6]), pos.getX(), pos.getY(), pos.getZ(),
                        (hasTracked) ? 1 : 0, (doIntegrate) ? 1 : 0);

                frames++;

                timings[0] = System.nanoTime();
                haveDepthImage = depthCamera.pollDepth(depthImageInput);
                videoCamera.skipVideoFrame();
            }
        }
    }

    @Override
    public void configure(Device device) {
        super.configure(device);

        initialPosition = Float3.mult(config.getOffset(), volumeDims);

        frames = 0;
    }

}
