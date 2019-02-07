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
package kfusion.java.devices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import ar.com.hjg.pngj.IImageLine;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngReader;
import ar.com.hjg.pngj.PngReaderByte;
import kfusion.java.common.AbstractLogger;
import kfusion.java.common.KfusionConfig;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat;

public class TUMRGBDevice extends AbstractLogger implements Device {
    final private static float DEPTH_SCALE_FACTOR = 1e-3f;
    final private static Float4 CAMERA = new Float4(new float[] { 481.2f, 480f, 319.5f, 239.5f });

    private final static String INDEX_FILE = "associations.txt";
    private final String groundTruthFile;

    private final String path;

    private long frameCount;
    private final List<String> videoFiles;
    private final List<String> depthFiles;
    private VectorFloat[] referencePoses;
    private final Float3 referenceTranslation;
    private final Float4 referenceRotation;

    private int videoFrameIndex;
    private int depthFrameIndex;

    private boolean running;

    public TUMRGBDevice(final String path, final String groundTruthFile) {
        this.path = path;
        this.groundTruthFile = groundTruthFile;
        this.running = false;
        videoFiles = new ArrayList<String>();
        depthFiles = new ArrayList<String>();
        referenceTranslation = new Float3();
        referenceRotation = new Float4();
    }

    public void init() {
        info("Initialising");

        // read associations file
        frameCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(path + "/" + INDEX_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String[] values = line.split(" ");
                if (values.length == 4) {
                    depthFiles.add(values[1]);
                    videoFiles.add(values[3]);
                    frameCount++;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        referencePoses = new VectorFloat[(int) frameCount];
        try (BufferedReader reader = new BufferedReader(new FileReader(path + "/" + groundTruthFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String[] values = line.split(" ");
                if (values.length == 8) {
                    int index = Integer.parseInt(values[0]);
                    final VectorFloat vector = new VectorFloat(7);
                    for (int i = 0; i < 7; i++) {
                        vector.set(i, Float.parseFloat(values[i + 1]));
                    }
                    referencePoses[index] = vector;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        videoFrameIndex = 0;
        depthFrameIndex = 0;

    }

    public boolean pollVideo(ImageByte3 videoImage) {
        boolean result = videoFrameIndex < frameCount ? true : false;

        if (result) {
            final String file = path + "/" + videoFiles.get(videoFrameIndex);
            PngReaderByte pngr = new PngReaderByte(new File(file));
            debug("[%d]: file=%s, rows=%d, cols=%d, channels=%d", videoFrameIndex, file, pngr.imgInfo.rows, pngr.imgInfo.cols, pngr.imgInfo.channels);
            ByteBuffer bb = videoImage.asBuffer();
            bb.rewind();

            for (int row = 0; row < pngr.imgInfo.rows; row++) {
                final IImageLine l1 = pngr.readRow();
                bb.put(((ImageLineByte) l1).getScanline());
            }

            bb.flip();
            pngr.end();
            videoFrameIndex++;
        }

        return result;
    }

    public boolean pollDepth(ImageFloat depthImage) {
        boolean result = depthFrameIndex < frameCount ? true : false;

        if (result) {
            final String file = path + "/" + depthFiles.get(depthFrameIndex);
            PngReader pngr = new PngReader(new File(file));
            debug("[%d]: file=%s, rows=%d, cols=%d, channels=%d", depthFrameIndex, file, pngr.imgInfo.rows, pngr.imgInfo.cols, pngr.imgInfo.channels);

            int index = 0;

            for (int row = 0; row < pngr.imgInfo.rows; row++) {
                final ImageLineInt l1 = (ImageLineInt) pngr.readRow();
                for (int col = 0; col < pngr.imgInfo.cols; col++) {
                    int value = l1.getElem(col) / 5;

                    depthImage.set(index, (float) value);
                    index++;
                }
            }

            pngr.end();

            // read reference pose info
            if (referencePoses[depthFrameIndex] != null) {
                referenceTranslation.loadFromBuffer(referencePoses[depthFrameIndex].subVector(0, 3).asBuffer());

                referenceRotation.loadFromBuffer(referencePoses[depthFrameIndex].subVector(3, 4).asBuffer());
            }

            depthFrameIndex++;
        } else {
            running = false;
        }

        return result;
    }

    @Override
    public int getHeight() {
        return 480;
    }

    @Override
    public int getWidth() {

        return 640;
    }

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
    }

    public void shutdown() {

    }

    public boolean isRunning() {
        return running;
    }

    public String toString() {
        return "TUM-RGB-D File Reader";
    }

    @Override
    public boolean hasReferencePose() {
        return true;
    }

    @Override
    public Float3 getTranslation() {
        return referenceTranslation;
    }

    @Override
    public Float4 getRotation() {
        return referenceRotation;
    }

    @Override
    public <T extends KfusionConfig> void updateModel(T config) {
        config.setDepthScaleFactor(DEPTH_SCALE_FACTOR);
        config.setCamera(CAMERA);
    }

    @Override
    public void skipVideoFrame() {

    }

}
