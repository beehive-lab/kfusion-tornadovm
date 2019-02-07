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

import kfusion.java.common.Utils;
import kfusion.java.devices.TestingDevice;
import kfusion.java.pipeline.AbstractPipeline;
import kfusion.tornado.common.TornadoModel;
import uk.ac.manchester.tornado.api.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.collections.types.Short2;
import uk.ac.manchester.tornado.api.collections.types.VolumeShort2;

public class IntegrationPipeline extends AbstractPipeline<TornadoModel> {
    public IntegrationPipeline(TornadoModel config) {
        super(config);
    }

    private VolumeShort2 refVolume;

    private static String makeFilename(String path, int frame, String kernel, String variable, boolean isInput) {
        return String.format("%s/%04d_%s_%s_%s", path, frame, kernel, variable, (isInput) ? "in" : "out");
    }

    private void loadFrame(String path, int index) {
        try {
            refVolume = volume.duplicate();

            Utils.loadData(makeFilename(path, index, "integration", "volume", true), volume.asBuffer());

            Utils.loadData(makeFilename(path, index, "integration", "volume", false), refVolume.asBuffer());
            Utils.loadData(makeFilename(path, index, "integration", "volumeDims", true), volumeDims.asBuffer());
            Utils.loadData(makeFilename(path, index, "integration", "k", true), scaledCamera.asBuffer());
            GraphicsMath.getCameraMatrix(scaledCamera, K);
            Utils.loadData(makeFilename(path, index, "integration", "pose", true), currentView.getPose().asBuffer());
            Utils.loadData(makeFilename(path, index, "integration", "depths", true), scaledDepthImage.asBuffer());

            Matrix4x4Float refInversePose = new Matrix4x4Float();
            Utils.loadData(makeFilename(path, index, "integration", "inversePose", true), refInversePose.asBuffer());

            Matrix4x4Float refK = new Matrix4x4Float();
            Utils.loadData(makeFilename(path, index, "integration", "cameraMatrix", true), refK.asBuffer());

            // System.out.printf("ref inv
            // pose:\n%s\n",refInversePose.toString(FloatOps.fmt4em));
            // System.out.printf("ref K :\n%s\n",refK.toString(FloatOps.fmt4em));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void execute() {

        integrate();

    }

    public boolean validate(String path, int index) {

        int errors = 0;
        for (int y = 0; y < volume.Y(); y++)
            for (int x = 0; x < volume.X(); x++)
                for (int z = 0; z < volume.Z(); z++) {
                    if (!TornadoMath.isEqual(volume.get(x, y, z).asBuffer().array(), refVolume.get(x, y, z).asBuffer().array())) {
                        final Short2 calc = volume.get(x, y, z);
                        final Short2 ref = refVolume.get(x, y, z);
                        // if(x==83 && y==68 && z== 203)
                        // System.out.printf("[%d, %d, %d] error: %s !=
                        // %s\n",x,y,z,calc.toString(),ref.toString());
                        errors++;
                    }
                }
        double pctBad = (((double) errors) / ((double) volume.X() * volume.Y() * volume.Z())) * 100.0;
        System.out.printf("\tfound %d bad values ( %.2f %%) \n", errors, pctBad);

        return errors == 0;
    }

    public static void main(String[] args) {
        final String path = args[0];
        final int numFrames = Integer.parseInt(args[1]);
        TornadoModel config = new TornadoModel();

        config.setDevice(new TestingDevice());

        IntegrationPipeline kernel = new IntegrationPipeline(config);
        kernel.reset();

        int validFrames = 0;
        for (int i = 0; i < numFrames; i++) {
            // int i = 46;
            System.out.printf("frame %d:\n", i);
            kernel.loadFrame(path, i);
            kernel.execute();
            boolean valid = kernel.validate(path, i);
            System.out.printf("\tframe %s valid\n", (valid) ? "is" : "is not");
            if (valid)
                validFrames++;
        }

        double pctValid = (((double) validFrames) / ((double) numFrames)) * 100.0;
        System.out.printf("Found %d valid frames (%.2f %%)\n", validFrames, pctValid);

    }

}
