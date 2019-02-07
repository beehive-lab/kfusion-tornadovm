/*
 *    This file is part of Slambench-Tornado: A Tornado version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-tornado
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
package kfusion.pipeline.java;

import kfusion.java.common.KfusionConfig;
import kfusion.java.common.Utils;
import kfusion.java.devices.TestingDevice;
import kfusion.java.pipeline.AbstractPipeline;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat;

public class TrackingPipeline extends AbstractPipeline<KfusionConfig> {

    public TrackingPipeline(KfusionConfig config) {
        super(config);
        // TODO Auto-generated constructor stub
    }

    private static String makeFilename(String path, int frame, String kernel, String variable, boolean isInput) {
        return String.format("%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in" : "out");
    }

    private void loadFrame(String path, int index) {
        try {

            Utils.loadData(makeFilename(path, index, "tracking", "ScaledDepth", true), filteredDepthImage.asBuffer());
            Utils.loadData(makeFilename(path, index, "tracking", "k", true), scaledCamera.asBuffer());

            GraphicsMath.getInverseCameraMatrix(scaledCamera, scaledInvK);
            GraphicsMath.getCameraMatrix(scaledCamera, K);

            Utils.loadData(makeFilename(path, index, "tracking", "pose", true), currentView.getPose().asBuffer());
            Utils.loadData(makeFilename(path, index, "tracking", "raycastPose", true), referenceView.getPose().asBuffer());

            Utils.loadData(makeFilename(path, index, "tracking", "vertex", true), referenceView.getVerticies().asBuffer());
            Utils.loadData(makeFilename(path, index, "tracking", "normal", true), referenceView.getNormals().asBuffer());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        final String path = args[0];
        final int numFrames = Integer.parseInt(args[1]);
        KfusionConfig config = new KfusionConfig();

        config.setDevice(new TestingDevice());

        TrackingPipeline kernel = new TrackingPipeline(config);
        kernel.reset();

        int validFrames = 0;
        for (int i = 0; i < numFrames; i++) {
            System.out.printf("frame %d:\n", i);
            kernel.loadFrame(path, i);
            kernel.execute();
            boolean valid = kernel.validate(path, i);
            System.out.printf("\tframe %s valid\n", (valid) ? "is" : "is not");
            if (valid) {
                validFrames++;
            }
        }
        double pctValid = (((double) validFrames) / ((double) numFrames)) * 100.0;
        System.out.printf("Found %d valid frames (%.2f %%)\n", validFrames, pctValid);
    }

    @Override
    public void execute() {
        boolean hasTracked = estimatePose();
        System.out.printf("[%d]: %s\n", frames, hasTracked);
    }

    public boolean validate(String path, int index) {
        final VectorFloat values = new VectorFloat(32);
        final Matrix4x4Float refPose = new Matrix4x4Float();

        try {
            Utils.loadData(makeFilename(path, index, "tracking", "reductionoutput", false), values.asBuffer());
            Utils.loadData(makeFilename(path, index, "tracking", "pose", false), refPose.asBuffer());

        } catch (Exception e) {
            e.printStackTrace();
        }

        boolean match = true;
        if (!FloatOps.compare(trackingResult.getError(), values.get(0))) {
            System.out.printf("\terror       : %.4e != %.4e (ref)\n", trackingResult.getError(), values.get(0));
            match = false;
        }
        if (!FloatOps.compare(trackingResult.getTracked(), values.get(28)))
            ;
        {
            System.out.printf("\ttracked     : %.4e != %.4e (ref)\n", trackingResult.getTracked(), values.get(28));
            match = false;
        }
        if (!FloatOps.compare(trackingResult.getTooFar(), values.get(29)))
            ;
        {
            System.out.printf("\ttoo far     : %.4e != %.4e (ref)\n", trackingResult.getTooFar(), values.get(29));
            match = false;
        }
        if (!FloatOps.compare(trackingResult.getWrongNormal(), values.get(30)))
            ;
        {
            System.out.printf("\twrong normal: %.4e != %.4e (ref)\n", trackingResult.getWrongNormal(), values.get(30));
            match = false;
        }
        if (!FloatOps.compare(trackingResult.getOther(), values.get(31)))
            ;
        {
            System.out.printf("\tother       : %.4e != %.4e (ref)\n", trackingResult.getOther(), values.get(31));
            match = false;
        }
        Matrix4x4Float calcPose = trackingResult.getPose();
        int errors = 0;
        for (int y = 0; y < refPose.M(); y++) {
            for (int x = 0; x < refPose.N(); x++) {
                if (!FloatOps.compare(calcPose.get(x, y), refPose.get(x, y))) {
                    errors++;
                }
            }
        }
        System.out.printf("\tpose has %d errors\n", errors);
        if (errors > 0) {
            System.out.printf("calc pose:\n%s\n", calcPose.toString(FloatOps.fmt4em));
            System.out.printf("ref  pose:\n%s\n", refPose.toString(FloatOps.fmt4em));
        }
        return match;
    }

}
