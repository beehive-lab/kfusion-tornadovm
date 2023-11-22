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
package kfusion.java.common;

import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.ByteBuffer;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.Animator;

import kfusion.java.devices.Device;
import kfusion.tornado.algorithms.Renderer;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageByte4;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;

@SuppressWarnings("serial")
public class Viewer extends GLCanvas implements GLEventListener {

    private static String TITLE = "Image Viewer";
    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 480;

    private static Device device;
    private ImageByte4 renderedDepthFrame;

    private final ImageFloat depthImage;
    private final ImageByte3 videoImage;

    private final KfusionConfig config;

    @Override
    public void init(GLAutoDrawable drawable) {

    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        gl.glLoadIdentity();

        gl.glViewport(0, 0, height, width);
        gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
        gl.glLoadIdentity();

        gl.glColor3f(1.0f, 1.0f, 1.0f);
        gl.glRasterPos2f(-1f, 1f);
        gl.glOrthof(-0.375f, width - 0.375f, height - 0.375f, -0.375f, -1f, 1f);
        gl.glPixelZoom(1f, -1f);
    }

    @Override
    public void display(GLAutoDrawable drawable) {

        GL2 gl = drawable.getGL().getGL2();

        /*
         * Uncommenting below resets canvas to black on every frame
         */
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
        // gl.glLoadIdentity();

        gl.glRasterPos2i(0, 0);

        boolean hasDepth = device.pollDepth(depthImage);
        if (hasDepth) {
            ImageFloat.scale(depthImage, 1e-3f);
            Renderer.renderDepth(renderedDepthFrame, depthImage, config.getNearPlane(), config.getFarPlane());
            drawImageRGBA(renderedDepthFrame, gl, 0, IMAGE_HEIGHT);
        }

        boolean hasVideo = device.pollVideo(videoImage);
        if (hasVideo) {
            drawImageRGB(videoImage, gl, IMAGE_WIDTH, IMAGE_HEIGHT);
        }

        gl.glFlush();

    }

    private final void drawImageRGBA(ImageByte4 image, final GL2 gl, int x, int y) {
        final ByteBuffer bb = image.asBuffer();
        bb.rewind();

        gl.glWindowPos2i(x, y);
        gl.glDrawPixels(image.X(), image.Y(), GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, bb);
    }

    private final void drawImageRGB(final ImageByte3 image, final GL2 gl, int x, int y) {
        final ByteBuffer bb = image.asBuffer();
        bb.rewind();

        gl.glWindowPos2i(x, y);
        gl.glDrawPixels(image.X(), image.Y(), GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, bb);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {

    }

    public Viewer(final String configFile, GLCapabilities caps) {
        super(caps);
        config = new KfusionConfig();
        config.loadSettingsFile(configFile);

        this.addGLEventListener(this);

        device = config.discoverDevices()[0];
        System.out.printf("Using device: %s\n", device.toString());
        device.init();

        renderedDepthFrame = new ImageByte4(IMAGE_WIDTH, IMAGE_HEIGHT);
        depthImage = new ImageFloat(IMAGE_WIDTH, IMAGE_HEIGHT);
        videoImage = new ImageByte3(IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    public static void main(final String[] args) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                GLProfile glp = GLProfile.getDefault();
                GLCapabilities caps = new GLCapabilities(glp);
                caps.setDoubleBuffered(false);
                caps.setHardwareAccelerated(true);

                GLCanvas canvas = new Viewer(args[0], caps);
                canvas.setPreferredSize(new Dimension(IMAGE_WIDTH * 2, IMAGE_HEIGHT));

                // final FPSAnimator animator = new FPSAnimator(canvas,FPS,true);
                final Animator animator = new Animator(canvas);

                final JFrame frame = new JFrame();

                frame.getContentPane().add(canvas);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        new Thread() {
                            @Override
                            public void run() {
                                if (animator.isStarted())
                                    animator.stop();
                                System.exit(0);
                            }
                        }.start();
                    }
                });

                canvas.addKeyListener(new KeyAdapter() {

                    @Override
                    public void keyPressed(KeyEvent e) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_SPACE:
                                device.start();
                                break;
                            case KeyEvent.VK_Q:
                                if (animator.isStarted())
                                    animator.stop();
                                System.exit(0);
                                break;
                            default:
                                System.out.println("Unknown KeyEvent");
                        }
                    }

                });

                // frame.getContentPane().add(new JButton("hit me"));

                frame.setTitle(TITLE);
                frame.pack();
                frame.setVisible(true);
                animator.start();
            }

        });
    }

}
