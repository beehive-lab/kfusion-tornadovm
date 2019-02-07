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
package kfusion.ui;

import com.jogamp.opengl.util.Animator;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.*;

import kfusion.java.common.KfusionConfig;
import kfusion.java.devices.Device;

public class WorkbenchFrame<T extends KfusionConfig> extends JFrame implements WindowListener {

    final private Animator animator;
    @SuppressWarnings("unused")
    private GLCanvas canvas;
    private Timer timer;
    private final T config;
    private static final long serialVersionUID = 382257735843448290L;

    public WorkbenchFrame(final T config, GLCanvas canvas) {
        this.config = config;
        setTitle("KFusion Workbench");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(this);

        animator = new Animator();
        animator.setRunAsFastAsPossible(true);

        final ModelConfigPanel<T> modelConfigPanel = new ModelConfigPanel<>(config, animator);

        this.canvas = canvas;
        canvas.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_Q:
                        config.setQuit();
                        System.exit(0);
                        break;
                    case KeyEvent.VK_R:
                        config.setReset();
                        break;
                    case KeyEvent.VK_SPACE:
                        if (animator.isStarted()) {
                            stop();
                        } else {
                            config.setReset();
                            start();
                        }
                        break;
                    case KeyEvent.VK_T:
                        config.setDrawDepth(!config
                                .drawDepth());
                        break;
                    case KeyEvent.VK_LEFT:
                        config.rotateNegativeY();
                        break;
                    case KeyEvent.VK_RIGHT:
                        config.rotatePositiveY();
                        break;
                    case KeyEvent.VK_UP:
                        config.rotatePositiveX();
                        break;
                    case KeyEvent.VK_DOWN:
                        config.rotateNegativeX();
                        break;

                    case KeyEvent.VK_D:
                        config.toggleDebug();
                        break;
                }
            }

        });

        final JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.PAGE_AXIS));
        p.setPreferredSize(canvas.getPreferredSize());
        p.add(canvas);

        final JSplitPane p1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                modelConfigPanel, p);
        p1.setDividerLocation(150);

        getContentPane().add(p1);

        animator.add(canvas);

        pack();

        setSize(640 * 2 + 200, 480 + 200);

    }

    private void start() {
        if (!animator.isAnimating()) {
            animator.start();
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void stop() {
        if (animator.isAnimating()) {
            animator.stop();
        }
        if (timer.isRunning()) {
            timer.stop();
        }
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
