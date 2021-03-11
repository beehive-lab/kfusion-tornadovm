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
package kfusion.pipeline.tornado;

import static uk.ac.manchester.tornado.api.collections.types.Float4.mult;

import kfusion.java.common.Utils;
import kfusion.java.devices.Device;
import kfusion.java.devices.TestingDevice;
import kfusion.java.pipeline.AbstractPipeline;
import kfusion.tornado.algorithms.IterativeClosestPoint;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.graphics.ImagingOps;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.FloatingPointError;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat;
import uk.ac.manchester.tornado.matrix.MatrixFloatOps;
import uk.ac.manchester.tornado.matrix.MatrixMath;
import uk.ac.manchester.tornado.api.utils.TornadoUtilities;

public class TrackingPipeline extends AbstractPipeline<TornadoModel> {

    public TrackingPipeline(TornadoModel config) {
        super(config);
    }

    private static String makeFilename(String path, int frame, String kernel, String variable, boolean isInput) {
        return String.format("%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in" : "out");
    }

    private TaskSchedule graph1;
    private TaskSchedule graph2;
    private Matrix4x4Float[] scaledInvKs;

    @Override
    public void configure(Device device) {
        super.configure(device);

        scaledInvKs = new Matrix4x4Float[pyramidIterations.length];

        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();

        final int iterations = pyramidIterations.length;
        for (int i = 0; i < iterations; i++) {
            scaledInvKs[i] = new Matrix4x4Float();
        }

        graph1 = new TaskSchedule("s0").streamIn(pyramidDepths[0]);

        for (int i = 1; i < iterations; i++) {
            graph1.task("resize" + i, ImagingOps::resizeImage6, pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2).streamOut(pyramidDepths[i]);
        }

        graph2 = new TaskSchedule("s1");
        for (int i = 0; i < iterations; i++) {
            graph2.streamIn(scaledInvKs[i]).task("d2v" + i, GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i])
                    .task("v2n" + i, GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]).streamOut(pyramidVerticies[i], pyramidNormals[i]);
        }

        graph1.mapAllTo(config.getTornadoDevice());
        graph2.mapAllTo(config.getTornadoDevice());
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

            for (int i = 0; i < pyramidIterations.length; i++) {
                if (config.debug()) {
                    info("level: %d", i);
                }

                final Float4 cameraDup = mult(scaledCamera, 1f / (float) (1 << i));
                GraphicsMath.getInverseCameraMatrix(cameraDup, scaledInvKs[i]);

                if (config.debug()) {
                    info("scaled camera: %s", cameraDup.toString());
                    info("scaled InvK:\n%s", scaledInvKs[i].toString());
                    info(String.format("size: {%d,%d}", pyramidVerticies[i].X(), pyramidVerticies[i].Y()));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        final String path = args[0];
        final int numFrames = Integer.parseInt(args[1]);
        TornadoModel config = new TornadoModel();

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
    protected boolean estimatePose() {
        if (config.debug()) {
            info("============== estimaing pose ==============");
        }

        long start = System.nanoTime();
        graph1.execute();

        for (ImageFloat depths : pyramidDepths) {
            System.out.println("depths: " + depths.summerise());
        }

        graph2.execute();
        long end = System.nanoTime();
        // graph1.dumpTimes();
        // graph2.dumpTimes();
        System.out.printf("elapsed time=%s\n", TornadoUtilities.elapsedTimeInSeconds(start, end));

        final Matrix4x4Float invReferencePose = referenceView.getPose().duplicate();
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
                    info(String.format("verticies: " + pyramidVerticies[level].summerise()));
                    info(String.format("normals: " + pyramidNormals[level].summerise()));
                    info(String.format("ref verticies: " + referenceView.getVerticies().summerise()));
                    info(String.format("ref normals: " + referenceView.getNormals().summerise()));
                    info(String.format("pose: \n%s\n", pose.toString(FloatOps.FMT_4_EM)));
                }

                IterativeClosestPoint.trackPose(pyramidTrackingResults[level], pyramidVerticies[level], pyramidNormals[level], referenceView.getVerticies(), referenceView.getNormals(), pose,
                        projectReference, distanceThreshold, normalThreshold);

                boolean updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, pyramidTrackingResults[level], pose, 1e-5f);

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
        // return false;
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

        // System.out.printf("expected: %s\n",values.toString("%.4e"));
        boolean match = true;

        final float errorUlp = FloatOps.findMaxULP(trackingResult.getError(), values.get(0));
        if (errorUlp > 5f) {
            match = false;
        }
        System.out.printf("\terror       : %x <-> %x (ref) error ulp = %f\n", Float.floatToIntBits(trackingResult.getError()), Float.floatToRawIntBits(values.get(0)), errorUlp);

        final float trackedUlp = FloatOps.findMaxULP(trackingResult.getTracked(), values.get(28));
        if (trackedUlp > 5f) {
            match = false;
        }
        System.out.printf("\ttracked     : %.4e <-> %.4e (ref) error ulp = %f\n", trackingResult.getTracked(), values.get(28), trackedUlp);

        final float tooFarUlp = FloatOps.findMaxULP(trackingResult.getTooFar(), values.get(29));
        if (tooFarUlp > 5f) {
            match = false;
        }
        System.out.printf("\ttoo far     : %.4e <-> %.4e (ref) error ulp = %f\n", trackingResult.getTooFar(), values.get(29), tooFarUlp);

        final float wrongNormalUlp = FloatOps.findMaxULP(trackingResult.getWrongNormal(), values.get(30));
        if (wrongNormalUlp > 5f) {
            match = false;
        }

        System.out.printf("\twrong normal: %.4e <-> %.4e (ref) error ulp = %f\n", trackingResult.getWrongNormal(), values.get(30), wrongNormalUlp);

        final float otherUlp = FloatOps.findMaxULP(trackingResult.getOther(), values.get(31));
        if (otherUlp > 5f) {
            match = false;
        }
        System.out.printf("\tother       : %.4e <-> %.4e (ref) error ulp = %f\n", trackingResult.getOther(), values.get(31), otherUlp);

        final FloatingPointError poseError = trackingResult.getPose().calculateULP(refPose);

        System.out.printf("\tpose            : error %s\n", poseError.toString());
        if (poseError.getMaxUlp() > 5f) {
            System.out.printf("calc pose:\n%s\n", trackingResult.getPose().toString(FloatOps.FMT_4_EM));
            System.out.printf("ref  pose:\n%s\n", refPose.toString(FloatOps.FMT_4_EM));
            match = false;
        }
        return match;
    }

}
