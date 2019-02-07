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
package kfusion.pipeline.tornado;

import static uk.ac.manchester.tornado.api.collections.types.Float4.mult;

import java.util.Arrays;

import kfusion.Utils;
import kfusion.devices.Device;
import kfusion.devices.TestingDevice;
import kfusion.pipeline.AbstractPipeline;
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
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.mm.TaskMetaDataInterface;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.utils.TornadoUtilities;
import uk.ac.manchester.tornado.matrix.MatrixFloatOps;
import uk.ac.manchester.tornado.matrix.MatrixMath;


public class ReducePipeline extends AbstractPipeline<TornadoModel> {

    public ReducePipeline(TornadoModel config) {
        super(config);
    }

    private static final int roundToWgs(int value, int wgs) {
        final int numWgs = value / wgs;
        return numWgs * wgs;
    }

    private static String makeFilename(String path, int frame, String kernel, String variable, boolean isInput) {
        return String.format("%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in" : "out");
    }

    private TaskSchedule graph1;
    private TaskSchedule graph2;
    private TaskSchedule[] trackingPyramid;
    private Matrix4x4Float[] scaledInvKs;
    private float[] icpResult;
    private Matrix4x4Float pyramidPose;
    private float[] icpResultIntermediate1;
    private float[] icpResultIntermediate2;

    private int cus;

    @Override
    public void configure(Device device) {
        super.configure(device);

        final TornadoDevice oclDevice = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        final long localMemSize = oclDevice.getDevice().getLocalMemorySize();
        cus = oclDevice.getDevice().getMaxComputeUnits();

        final float cuLF = Float.parseFloat(TornadoRuntime.getProperty("culf", "1.0"));
        final float wgLF = Float.parseFloat(TornadoRuntime.getProperty("wglf", "1.0"));

        final int maxBinsPerResource = (int) localMemSize / ((32 * 4) + 24);
        final int maxBinsPerCU = roundToWgs(maxBinsPerResource, 128);

        final int wgs = maxBinsPerCU * cus;

        System.out.printf("local mem size   : %s\n", TornadoUtilities.humanReadableByteCount(localMemSize, false));
        System.out.printf("num compute units: %d\n", cus);
        System.out.printf("max bins per cu  : %d\n", maxBinsPerCU);
        System.out.printf("reduce ratio 1   : %d\n", wgs);

        scaledInvKs = new Matrix4x4Float[pyramidIterations.length];

        pyramidPose = new Matrix4x4Float();
        pyramidDepths[0] = filteredDepthImage;
        pyramidVerticies[0] = currentView.getVerticies();
        pyramidNormals[0] = currentView.getNormals();
        icpResult = new float[32];
        icpResultIntermediate1 = new float[wgs * 32];
        icpResultIntermediate2 = new float[512 * 32];

        final int iterations = pyramidIterations.length;
        for (int i = 0; i < iterations; i++) {
            scaledInvKs[i] = new Matrix4x4Float();
        }

        graph1 = new TaskSchedule("s0").streamIn(pyramidDepths[0]);

        for (int i = 1; i < iterations; i++) {
            graph1.task("resizeImage" + i, ImagingOps::resizeImage6, pyramidDepths[i], pyramidDepths[i - 1], 2, eDelta * 3, 2).streamOut(pyramidDepths[i]);
        }

        // graph2 = new TaskSchedule();
        for (int i = 0; i < iterations; i++) {
            graph1.streamIn(scaledInvKs[i]).task("d2v" + i, GraphicsMath::depth2vertex, pyramidVerticies[i], pyramidDepths[i], scaledInvKs[i])
                    .task("v2n" + i, GraphicsMath::vertex2normal, pyramidNormals[i], pyramidVerticies[i]).streamOut(pyramidVerticies[i], pyramidNormals[i]);
        }
        graph1.streamIn(projectReference);

        trackingPyramid = new TaskSchedule[iterations];
        for (int i = 0; i < iterations; i++) {
            trackingPyramid[i] = new TaskSchedule("s0")
                    .streamIn(pyramidPose)
                    .task("track" + i, IterativeClosestPoint::trackPose,
                            pyramidTrackingResults[i], pyramidVerticies[i], pyramidNormals[i],
                            referenceView.getVerticies(), referenceView.getNormals(), pyramidPose,
                            projectReference, distanceThreshold, normalThreshold)
                    .prebuiltTask("optMapReduce" + i,
                            "optMapReduce",
                            "./opencl/optMapReduce.cl",
                            new Object[]{icpResultIntermediate1, pyramidTrackingResults[i], pyramidTrackingResults[i].X(), pyramidTrackingResults[i].Y()},
                            new Access[]{Access.WRITE, Access.READ, Access.READ, Access.READ},
                            oclDevice,
                            new int[]{wgs})
                    .streamOut(icpResultIntermediate1, pyramidTrackingResults[i])
                    .mapAllTo(oclDevice);

            TaskMetaDataInterface meta = trackingPyramid[i].getTask("s0.optMapReduce" + i).meta();
            meta.getGlobalWork()[0] = wgs;
            meta.getLocalWork()[0] = maxBinsPerCU;
            //@formatter:on
        }

        graph1.mapAllTo(oclDevice);
        // graph2.mapAllTo(config.getTornadoDevice());
    }

    private void loadFrame(String path, int index) {
        try {

            Utils.loadData(makeFilename(path, index, "tracking", "ScaledDepth", true), filteredDepthImage.asBuffer());
            System.out.println(filteredDepthImage.summerise());
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

        ReducePipeline kernel = new ReducePipeline(config);
        kernel.reset();

        int validFrames = 0;
        // for (int i = 0; i < numFrames; i++) {
        int i = 16;
        System.out.printf("frame %d:\n", i);
        kernel.loadFrame(path, i);
        kernel.execute();
        boolean valid = kernel.validate(path, i);
        System.out.printf("\tframe %s valid\n", (valid) ? "is" : "is not");
        if (valid) {
            validFrames++;
        }
        // }

        double pctValid = (((double) validFrames) / ((double) numFrames)) * 100.0;
        System.out.printf("Found %d valid frames (%.2f %%)\n", validFrames, pctValid);

    }

    @Override
    protected boolean estimatePose() {
        if (config.debug()) {
            info("============== estimaing pose ==============");
        }

        final Matrix4x4Float invReferencePose = referenceView.getPose().duplicate();
        MatrixFloatOps.inverse(invReferencePose);

        MatrixMath.sgemm(K, invReferencePose, projectReference);

        if (config.debug()) {
            info("camera matrix    :\n%s", K.toString());
            info("inv ref pose     :\n%s", invReferencePose.toString());
            info("project reference:\n%s", projectReference.toString());
        }

        long start = System.nanoTime();
        graph1.schedule().waitOn();

        for (ImageFloat depths : pyramidDepths) {
            System.out.println("depths: " + depths.summerise());
        }

        // graph2.schedule().waitOn();
        long end = System.nanoTime();
        graph1.dumpTimes();
        // graph2.dumpTimes();
        System.out.printf("elapsed time=%s\n", TornadoUtilities.elapsedTimeInSeconds(start, end));

        // perform ICP
        pyramidPose.set(currentView.getPose());
        config.getTornadoDevice().sync();
        for (int level = pyramidIterations.length - 1; level >= 0; level--) {
            for (int i = 0; i < pyramidIterations[level]; i++) {

                trackingPyramid[level].schedule().waitOn();

                trackingPyramid[level].dumpProfiles();
                trackingPyramid[level].dumpTimes();

                Arrays.fill(icpResult, 0);

                // IterativeClosestPoint.reduceIntermediate(icpResult, icpResultIntermediate1);
                for (int k = 0; k < cus; k++) {
                    final int index = k * 32;
                    for (int j = 0; j < 32; j++) {

                        icpResult[j] += icpResultIntermediate1[index + j];
                    }
                }

                // boolean updated = IterativeClosestPoint.estimateNewPose(config,
                // trackingResult,
                // pyramidTrackingResults[level], pyramidPose, 1e-5f);
                System.out.println("icp: " + Arrays.toString(icpResult));

                // IterativeClosestPoint.mapReduce(icpResultIntermediate1,
                // pyramidTrackingResults[level]);
                // IterativeClosestPoint.reduceIntermediate(icpResult, icpResultIntermediate1);
                // System.out.println("host: " + Arrays.toString(icpResult));
                final float[] refIcp = new float[32];
                IterativeClosestPoint.reduce(refIcp, pyramidTrackingResults[level]);

                System.out.println("ref: " + Arrays.toString(refIcp));

                System.out.println();

                trackingResult.resultImage = pyramidTrackingResults[level];
                final boolean updated = IterativeClosestPoint.estimateNewPose(config, trackingResult, icpResult, pyramidPose, 1e-5f);

                // System.out.printf("tracking: %s\n",trackingResult.toString());
                pyramidPose.set(trackingResult.getPose());

                if (updated) {
                    System.out.printf("updated!!!\n");
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
            System.out.printf("calc pose:\n%s\n", trackingResult.getPose().toString(FloatOps.fmt4em));
            System.out.printf("ref  pose:\n%s\n", refPose.toString(FloatOps.fmt4em));
            match = false;
        }
        return match;
    }

}
