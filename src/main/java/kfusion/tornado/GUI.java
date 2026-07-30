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

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

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

    // scripts/runGUI.sh derives this from the desktop's DPI scale (Xft.dpi)
    // and passes it as kfusion.ui.fontScale, so the config panels stay
    // readable on HiDPI Linux displays. This is deliberately a font/icon-only
    // scale rather than sun.java2d.uiScale: that property also scales the
    // GLCanvas's native pixel size, which AbstractOpenGLPipeline#display
    // doesn't account for (it draws at hardcoded pixel positions), leaving a
    // large blank margin around the actual camera/reconstruction views.
    private static void scaleUIDefaults() {
        final float scale = Float.parseFloat(System.getProperty("kfusion.ui.fontScale", "1"));
        if (scale == 1f) {
            return;
        }
        for (Object key : UIManager.getDefaults().keySet()) {
            final Object value = UIManager.get(key);
            if (value instanceof Font font) {
                UIManager.put(key, new FontUIResource(font.deriveFont(font.getSize2D() * scale)));
            }
        }
        // The checkbox/radio button tick icon is a fixed pixel-size L&F
        // resource, not derived from the font, so scaling fonts above
        // doesn't touch it - wrap it so it grows (e.g. "Use Tornado") too.
        scaleIcon("CheckBox.icon", scale);
        scaleIcon("RadioButton.icon", scale);
    }

    private static void scaleIcon(String key, float scale) {
        if (UIManager.get(key) instanceof Icon icon) {
            UIManager.put(key, new ScaledIcon(icon, scale));
        }
    }

    private record ScaledIcon(Icon delegate, float scale) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            final Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.scale(scale, scale);
            delegate.paintIcon(c, g2, 0, 0);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return Math.round(delegate.getIconWidth() * scale);
        }

        @Override
        public int getIconHeight() {
            return Math.round(delegate.getIconHeight() * scale);
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater( () -> {
                scaleUIDefaults();
                final TornadoModel config = new TornadoModel();
                if (System.getProperty("tornado.config") != null) {
                    TornadoRuntimeProvider.loadSettings(System.getProperty("tornado.config"));
                }

                // Start zoomed to whatever fits this screen, so the window
                // opens usable on a small display and fills a large/HiDPI
                // one instead of sitting tiny in its native 1x size (zoom is
                // still clamped to KfusionConfig.MAX_ZOOM by setZoom below).
                // The +/- keys can zoom further from there either way.
                final Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                config.setZoom(Math.min((screenBounds.width - RESERVED_SCREEN_WIDTH) / (float) BASE_CANVAS_WIDTH,
                        (screenBounds.height - RESERVED_SCREEN_HEIGHT) / (float) BASE_CANVAS_HEIGHT));
                final float fitZoom = config.getZoom();

                final TornadoConfigPanel tornadoConfig = new TornadoConfigPanel(config);
                final KfusionTornadoCanvas canvas = new KfusionTornadoCanvas(config, Math.round(BASE_CANVAS_WIDTH * fitZoom),
                        Math.round(BASE_CANVAS_HEIGHT * fitZoom), tornadoConfig);
                TornadoWorkbenchFrame frame = new TornadoWorkbenchFrame(config, canvas, tornadoConfig);
                frame.setVisible(true);
        });
    }
}
