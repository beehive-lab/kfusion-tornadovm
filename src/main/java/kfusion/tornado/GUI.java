/*
 *    This file is part of Slambench-Tornado: A Tornado version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-tornado
 *
 *    Copyright (c) 2013-2017 APT Group, School of Computer Science,
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
package kfusion.tornado;

import java.awt.EventQueue;
import kfusion.TornadoModel;
import kfusion.ui.KfusionTornadoCanvas;
import kfusion.ui.TornadoConfigPanel;
import kfusion.ui.TornadoWorkbenchFrame;

import static java.lang.System.getProperty;
import static uk.ac.manchester.tornado.common.Tornado.loadSettings;

public class GUI {

    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                final TornadoModel config = new TornadoModel();
                if (getProperty("tornado.config") != null) {
                    loadSettings(getProperty("tornado.config"));
                }

                final TornadoConfigPanel tornadoConfig = new TornadoConfigPanel(config);
                final KfusionTornadoCanvas canvas = new KfusionTornadoCanvas(config, 660 * 2, 500, tornadoConfig);
                TornadoWorkbenchFrame frame = new TornadoWorkbenchFrame(config, canvas, tornadoConfig);
                frame.setVisible(true);
            }

        });

    }

}
