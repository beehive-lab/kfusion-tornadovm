/*
 *  This file is part of Tornado-KFusion: A Java version of the KFusion computer vision
 *  algorithm running on TornadoVM.
 *  URL: https://github.com/beehive-lab/kfusion-tornadovm
 *
 *  Copyright (c) 2013-2019, 2024, APT Group, Department of Computer Science,
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

import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import kfusion.tornado.common.TornadoModel;
import kfusion.tornado.ui.KfusionTornadoCanvas;
import kfusion.tornado.ui.TornadoConfigPanel;
import kfusion.tornado.ui.TornadoFramesPanel;
import kfusion.tornado.ui.TornadoWorkbenchFrame;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

public class GUI {

    // The canvas's content (see AbstractOpenGLPipeline#display) is drawn at
    // fixed pixel positions sized for this "natural" (zoom == 1) canvas size.
    private static final int BASE_CANVAS_WIDTH = 660 * 2;
    private static final int BASE_CANVAS_HEIGHT = 500;

    // Reserved for the config panels above the canvas + window chrome, when
    // computing how much of the screen the canvas can actually use.
    private static final int RESERVED_SCREEN_WIDTH = 40;
    private static final int RESERVED_SCREEN_HEIGHT = 360;

    public static void main(String[] args) {
        EventQueue.invokeLater( () -> {
                final TornadoModel config = new TornadoModel();
                if (System.getProperty("tornado.config") != null) {
                    TornadoRuntimeProvider.loadSettings(System.getProperty("tornado.config"));
                }

                // Start zoomed to whatever fits this screen (capped at 1x - no
                // point zooming past the content's native resolution by
                // default), so the window opens usable even on a small
                // display. The +/- keys can zoom in further from there.
                final Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                final float fitZoom = Math.min(1f, Math.min((screenBounds.width - RESERVED_SCREEN_WIDTH) / (float) BASE_CANVAS_WIDTH,
                        (screenBounds.height - RESERVED_SCREEN_HEIGHT) / (float) BASE_CANVAS_HEIGHT));
                config.setZoom(fitZoom);

                final TornadoConfigPanel tornadoConfig = new TornadoConfigPanel(config);
                final KfusionTornadoCanvas canvas = new KfusionTornadoCanvas(config, Math.round(BASE_CANVAS_WIDTH * fitZoom),
                        Math.round(BASE_CANVAS_HEIGHT * fitZoom), tornadoConfig);
                TornadoWorkbenchFrame frame = new TornadoWorkbenchFrame(config, canvas, tornadoConfig);
                frame.setVisible(true);
        });
    }
}
