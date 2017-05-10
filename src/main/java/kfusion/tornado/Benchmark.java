/*
 * Copyright 2017 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kfusion.tornado;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import kfusion.TornadoModel;
import kfusion.devices.Device;
import kfusion.pipeline.TornadoBenchmarkPipeline;
import tornado.collections.types.Float4;

import static java.lang.System.getProperty;
import static tornado.common.Tornado.loadSettings;

public class Benchmark {

    public static void main(String[] args) {

        final TornadoModel config = new TornadoModel();
        config.loadSettingsFile(args[0]);

        if (getProperty("tornado.config") != null) {
            loadSettings(getProperty("tornado.config"));
            config.loadSettingsFile(getProperty("tornado.config"));
//            config.reset();
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

        // finish
        System.out.printf("Summary: time=%.2f, frames=%d\n", elapsed, pipeline.getProcessedFrames());
    }

}
