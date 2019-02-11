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
package kfusion.tornado;

import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import kfusion.java.algorithms.IterativeClosestPoint;
import kfusion.java.algorithms.TrackingResult;
import kfusion.java.common.Utils;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float8;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat8;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.collections.types.MatrixFloat;

public class ICPTesting {

    public static void loadTrackData(final String file, final ImageFloat8 dest) throws Exception {
        final FileInputStream fis = new FileInputStream(file);
        final FileChannel vChannel = fis.getChannel();

        final ByteBuffer bb = ByteBuffer.allocate((int) vChannel.size());
        bb.order(ByteOrder.LITTLE_ENDIAN);

        vChannel.read(bb);
        bb.flip();

        vChannel.close();

        int i = 0;
        while (bb.hasRemaining()) {
            final int x = i % dest.X();
            final int y = i / dest.X();
            dest.set(x, y, slurpTrackData(bb));

            i++;
        }

        fis.close();

    }

    public static void main(final String[] args) {
        final ICPTesting testing = new ICPTesting();

        try {
            testing.setUp();

            testing.testTrack();
            testing.tearDown();

        } catch (final Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static Float8 slurpTrackData(final ByteBuffer buffer) {
        final Float8 result = new Float8();
        result.setS7(buffer.getInt());
        result.setS6(buffer.getFloat());
        for (int i = 0; i < 6; i++) {
            result.set(i, buffer.getFloat());
        }
        return result;
    }

    private float dist_threshold;

    final private String FILE_PATH = "/Users/jamesclarkson/Downloads/kfusion_ut_data";
    final int height = 240;
    final int width = 320;

    private ImageFloat3 inNormal;
    private ImageFloat3 inVertex;

    private float normal_threshold;
    private ImageFloat8 outputTruth;

    private ImageFloat3 refNormal;
    private ImageFloat3 refVertex;

    private ImageFloat8 results;

    final String track_prefix = "track_";

    private TaskSchedule graph;

    private Matrix4x4Float Ttrack;

    private Matrix4x4Float view;

    private final TornadoModel config = new TornadoModel();

    private boolean compareTrackData(final Float8 value, final Float8 ref) {
        boolean output = true;
        final int result = (int) value.getS7();

        if (!FloatOps.compareBits(value.getS7(), ref.getS7())) {
            output = false;
        } else if (result > 0) { // only get errors on negative results
            if (!FloatOps.compareBits(value.getS6(), ref.getS6())) {
                output = false;
            } else {
                for (int i = 0; i < 6; i++) {
                    if (!FloatOps.compareBits(value.get(i), ref.get(i))) {
                        output = false;
                        break;
                    }
                }
            }
        }
        return output;
    }

    @Before
    public void setUp() throws Exception {

        results = new ImageFloat8(width, height);
        inVertex = new ImageFloat3(width, height);
        inNormal = new ImageFloat3(width, height);
        refVertex = new ImageFloat3(width, height);
        refNormal = new ImageFloat3(width, height);
        Ttrack = new Matrix4x4Float();
        view = new Matrix4x4Float();

        outputTruth = new ImageFloat8(width, height);

        final float[] tmp = new float[1];
        Utils.loadData(String.format("%s/%sdist_threshold.in.%04d", FILE_PATH, track_prefix, 2), tmp);
        dist_threshold = tmp[0];

        Utils.loadData(String.format("%s/%snormal_threshold.in.%04d", FILE_PATH, track_prefix, 2), tmp);
        normal_threshold = tmp[0];

        // trackingData,errorData,results,inVertex,inNormal,refVertex,refNormal,Ttrack,view,dist_threshold,normal_threshold
        // final TornadoExecuteTask trackPose =
        // IterativeClosestPoint.trackCode.invoke(results, inVertex, inNormal,
        // refVertex, refNormal, Ttrack, view, dist_threshold, normal_threshold);
        // trackPose.disableJIT();
        // trackPose.meta().addProvider(DomainTree.class, domain);
        // trackPose.mapTo(EXTERNAL_GPU);
        // trackPose.loadFromFile("trackPose-bad.cl");
        graph = new TaskSchedule("s0").streamIn(inVertex, inNormal, refVertex, refNormal, Ttrack, view)
                .task("track", IterativeClosestPoint::trackPose, results, inVertex, inNormal, refVertex, refNormal, Ttrack, view, dist_threshold, normal_threshold).streamOut(results)
                .mapAllTo(config.getTornadoDevice());
        // ((TornadoTaskInvocation) trackInv).disableJIT();
        // trackInv.meta().addProvider(DomainTree.class, domain);
        // trackInv.mapTo(EXTERNAL_GPU);
        // ((TornadoTaskInvocation) trackInv).loadFromFile("trackPose2.cl");
        // trackInv.getStack().getEvent().waitOn();

        // makeVolatile(inVertex,inNormal,refVertex,refNormal,Ttrack,view);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testTrack() {

        final int[] frames = { 2, 13, 12, 14, 16, 15, 27, 26, 28, 30, 29, 40, 42, 41, 43, 54, 53, 55, 57, 56, 58, 60, 59, 62, 61 };
        // final int[] frames = {0,3,4,5,6,17,18,19,20,31,32,33,34,44,45,46,47};

        int badFrames = 0;

        for (int j = 1; j < frames.length; j++) {

            final int i = frames[j];
            try {
                Utils.loadData(String.format("%s/%sinVertex.in.%04d", FILE_PATH, track_prefix, i), inVertex.asBuffer());

                Utils.loadData(String.format("%s/%sinNormal.in.%04d", FILE_PATH, track_prefix, i), inNormal.asBuffer());

                Utils.loadData(String.format("%s/%srefVertex.in.%04d", FILE_PATH, track_prefix, i), refVertex.asBuffer());

                Utils.loadData(String.format("%s/%srefNormal.in.%04d", FILE_PATH, track_prefix, i), refNormal.asBuffer());

                Utils.loadData(String.format("%s/%sttrack.in.%04d", FILE_PATH, track_prefix, i), Ttrack.asBuffer());

                Utils.loadData(String.format("%s/%sview.in.%04d", FILE_PATH, track_prefix, i), view.asBuffer());

                loadTrackData(String.format("%s/%soutput.out.%04d", FILE_PATH, track_prefix, i), outputTruth);

            } catch (final Exception e) {
                fail("Unable to load data: " + e.getMessage());
            }

            if (config.useTornado()) {

                // 294, 229
                graph.schedule().waitOn();
                graph.dumpTimes();
            } else {
                final long start = System.nanoTime();
                IterativeClosestPoint.trackPose(results, inVertex, inNormal, refVertex, refNormal, Ttrack, view, dist_threshold, normal_threshold);
                final long end = System.nanoTime();
                System.out.printf("track: %f s\n", (end - start) * 1e-9f);
            }

            int errors = 0;
            int good = 0;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    final Float8 track = results.get(x, y);
                    final Float8 trackRef = outputTruth.get(x, y);
                    if (!compareTrackData(track, trackRef)) {
                        errors++;
                        // } else {
                        // System.out.printf("track: TrackData[%d,%d]\n\t(cal) %s\n\t(ref) %s\n", x,
                        // y, track.toString(), trackRef.toString());
                    }

                    if (((int) track.getS7()) == 1) {
                        good++;
                    }
                }
            }

            final double pct = (errors / (320.0 * 240.0)) * 100.0;
            System.out.printf("track: [%04d] %d errors (%.2f %%) good=%d\n", i, errors, pct, good);

            if (errors > 0) {
                badFrames++;
            }

            new MatrixFloat(32, 1);

            final TrackingResult result = new TrackingResult();
            result.setResultImage(results);
            IterativeClosestPoint.estimateNewPose(config, result, results, Ttrack, 1e-5f);
            System.out.printf("track: [%04d] solve=%s\n", i, result.toString());

        }

        if (badFrames > 0) {
            final double badFramesPct = ((double) badFrames / (double) frames.length) * 100;
            fail(String.format("Errors found on %d frames (%.2f %%)", badFrames, badFramesPct));
        }
    }

}
