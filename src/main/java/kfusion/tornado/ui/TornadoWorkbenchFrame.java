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
package kfusion.tornado.ui;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import com.jogamp.opengl.awt.GLCanvas;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import com.jogamp.opengl.util.Animator;

import kfusion.java.devices.Device;
import kfusion.tornado.common.TornadoModel;

public class TornadoWorkbenchFrame extends JFrame implements WindowListener {

    private static final String APP_TITLE = "KFusion TornadoVM Workbench";
    final private Animator animator;

    private Timer timer;
    private final TornadoModel config;

    // Reserved for the config panels above the canvas + window chrome, when
    // clamping the initial scrollable viewport to fit the screen.
    private static final int RESERVED_SCREEN_HEIGHT = 360;
    private static final int RESERVED_SCREEN_WIDTH = 40;

    private static final long serialVersionUID = 382257735843448290L;

    public TornadoWorkbenchFrame(final TornadoModel tornadoConfig, KfusionTornadoCanvas canvas, TornadoConfigPanel configPanel) {
        this.config = tornadoConfig;
        setTitle(APP_TITLE);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(this);

        animator = new Animator();
        animator.setRunAsFastAsPossible(true);

        final TornadoModelConfigPanel modelConfigPanel = new TornadoModelConfigPanel(tornadoConfig, animator, configPanel);

        // The canvas draws at fixed, absolute pixel coordinates, scaled by
        // config's zoom factor (see AbstractOpenGLPipeline#display and the
        // IDENTITY_PIXELSCALE comment in KfusionTornadoCanvas). GUI.main
        // already sized the canvas to fit this screen (zoom <= 1) before
        // constructing this frame; baseCanvasSize divides that back out to
        // recover the content's natural (zoom == 1) size, so resizeCanvasToZoom
        // (triggered by the +/- keys below) has a stable reference to scale
        // from regardless of what zoom the canvas started at.
        final Dimension baseCanvasSize = new Dimension(Math.round(canvas.getPreferredSize().width / tornadoConfig.getZoom()),
                Math.round(canvas.getPreferredSize().height / tornadoConfig.getZoom()));

        // AWT components default to an unbounded maximum size, so without an
        // explicit cap BoxLayout (and the JSplitPane below, which by default
        // gives 100% of any leftover resize space to its second/bottom
        // component) would stretch the canvas to fill whatever extra room the
        // frame ends up with - repainting that extra room as GL's cleared-to-
        // black background, with the real content still pinned in a corner.
        // Capping it to its own current size keeps it exactly as big as its
        // content and no bigger; resizeCanvasToZoom keeps this cap in sync
        // whenever zoom changes it.
        canvas.setMaximumSize(canvas.getPreferredSize());

        // Center the (now size-capped) canvas within whatever the scroll
        // pane's viewport ends up being, instead of leaving it pinned to the
        // viewport's top-left corner with blank space to its right/below.
        final JPanel centeringWrapper = new JPanel(new GridBagLayout());
        centeringWrapper.add(canvas);

        // The canvas can need more screen space than fits on a small display
        // if zoomed in past what GUI.main fit it to (it needs one screen
        // point per content pixel to stay aligned - no rescaling). Scrolling
        // is what lets the window still fit in that case: the viewport below
        // is clamped to the screen, and whatever doesn't fit is reachable by
        // scrolling or by zooming back out (-key) instead.
        final JScrollPane canvasScrollPane = new JScrollPane(centeringWrapper);
        final Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        canvasScrollPane.setPreferredSize(new Dimension(Math.min(canvas.getPreferredSize().width, screenBounds.width - RESERVED_SCREEN_WIDTH),
                Math.min(canvas.getPreferredSize().height, Math.max(150, screenBounds.height - RESERVED_SCREEN_HEIGHT))));

        canvas.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent keyEvent) {
                switch (keyEvent.getKeyCode()) {
                    case KeyEvent.VK_Q:
                        tornadoConfig.setQuit();
                        System.exit(0);
                        break;
                    case KeyEvent.VK_R:
                        tornadoConfig.setReset();
                        break;
                    case KeyEvent.VK_SPACE:
                        if (animator.isStarted()) {
                            stop();
                        } else {
                            tornadoConfig.setReset();
                            start();
                        }
                        break;
                    case KeyEvent.VK_T:
                        tornadoConfig.setDrawDepth(!tornadoConfig.drawDepth());
                        break;
                    case KeyEvent.VK_LEFT:
                        tornadoConfig.rotateNegativeY();
                        break;
                    case KeyEvent.VK_RIGHT:
                        tornadoConfig.rotatePositiveY();
                        break;
                    case KeyEvent.VK_UP:
                        tornadoConfig.rotatePositiveX();
                        break;
                    case KeyEvent.VK_DOWN:
                        tornadoConfig.rotateNegativeX();
                        break;

                    case KeyEvent.VK_D:
                        tornadoConfig.toggleDebug();
                        break;

                    case KeyEvent.VK_EQUALS:
                    case KeyEvent.VK_PLUS:
                    case KeyEvent.VK_ADD:
                        tornadoConfig.zoomIn();
                        resizeCanvasToZoom(canvas, centeringWrapper, baseCanvasSize, tornadoConfig.getZoom());
                        break;
                    case KeyEvent.VK_MINUS:
                    case KeyEvent.VK_SUBTRACT:
                        tornadoConfig.zoomOut();
                        resizeCanvasToZoom(canvas, centeringWrapper, baseCanvasSize, tornadoConfig.getZoom());
                        break;
                }
            }

        });

        final JSplitPane p1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, modelConfigPanel, canvasScrollPane);
        p1.setDividerLocation(200);

        getContentPane().add(p1);

        animator.add(canvas);

        pack();
    }

    private static void resizeCanvasToZoom(GLCanvas canvas, JPanel centeringWrapper, Dimension baseCanvasSize, float zoom) {
        final Dimension zoomed = new Dimension(Math.round(baseCanvasSize.width * zoom), Math.round(baseCanvasSize.height * zoom));
        canvas.setPreferredSize(zoomed);
        canvas.setMaximumSize(zoomed);
        canvas.setSize(zoomed);
        centeringWrapper.revalidate();
    }

    private void start() {
        if (!animator.isAnimating())
            animator.start();
        if (!timer.isRunning())
            timer.start();
    }

    private void stop() {
        if (animator.isAnimating())
            animator.stop();
        if (timer.isRunning())
            timer.stop();
    }

    @Override
    public void windowOpened(WindowEvent e) {

    }

    @Override
    public void windowClosing(WindowEvent e) {
        if (animator.isStarted()) {
            animator.stop();
        }

        final Device device = config.getDevice();
        if (device != null && device.isRunning()) {
            device.stop();
            device.shutdown();
        }
    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {

    }

    @Override
    public void windowDeiconified(WindowEvent e) {

    }

    @Override
    public void windowActivated(WindowEvent e) {

    }

    @Override
    public void windowDeactivated(WindowEvent e) {

    }
}
