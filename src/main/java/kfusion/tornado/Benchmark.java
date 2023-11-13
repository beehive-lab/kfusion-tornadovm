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

import java.io.FileNotFoundException;
import java.io.PrintStream;

import kfusion.java.devices.Device;
import kfusion.tornado.common.TornadoModel;
import kfusion.tornado.pipeline.TornadoBenchmarkPipeline;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

public class Benchmark {

    public static final String KFUSION_TORNADO_INFO = "KFussion Accelerated with Tornado";

    public static void printKFusionInfo() {
        System.out.println(KFUSION_TORNADO_INFO);
    }

    public static void main(String[] args) {

        printKFusionInfo();

        final TornadoModel config = new TornadoModel();
        config.loadSettingsFile(args[0]);

        if (System.getProperty("tornado.config") != null) {
            TornadoRuntime.loadSettings(System.getProperty("tornado.config"));
            config.loadSettingsFile(System.getProperty("tornado.config"));
        }

        PrintStream out = System.out;
        if (args.length == 2) {
            try {
                out = new PrintStream(args[1]);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.err.println("unable to write to file: " + args[1]);
                System.exit(-1);
            }
        }

        final TornadoBenchmarkPipeline pipeline = new TornadoBenchmarkPipeline(config, out);

        final Device device = config.discoverDevices()[0];
        device.init();

        device.updateModel(config);

        // update model config here
        config.setDevice(device);
        config.setCamera(new Float4(481.2f, 480f, 320f, 240f));

        pipeline.reset();

        // execute benchmark
        final long start = System.nanoTime();
        pipeline.execute();
        final long stop = System.nanoTime();
        final double elapsed = (stop - start) * 1e-9;
        final double framesPerSecond = pipeline.getProcessedFrames() / elapsed;

        System.out.printf("Summary: time=%.2f, frames=%d, FPS=%.2f\n", elapsed, pipeline.getProcessedFrames(), framesPerSecond);
    }
}
