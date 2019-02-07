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
package kfusion.java;

import java.io.FileNotFoundException;
import java.io.PrintStream;

import kfusion.KfusionConfig;
import kfusion.devices.Device;
import kfusion.pipeline.BenchmarkPipeline;
import uk.ac.manchester.tornado.api.collections.types.Float4;

public class Benchmark {

    public static void main(String[] args) {

        final KfusionConfig config = new KfusionConfig();
        config.loadSettingsFile(args[0]);

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

        final BenchmarkPipeline<KfusionConfig> pipeline = new BenchmarkPipeline<>(config, out);

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
