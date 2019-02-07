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
package kfusion;

import static javax.media.opengl.fixedfunc.GLMatrixFunc.GL_MODELVIEW;
import static javax.media.opengl.fixedfunc.GLMatrixFunc.GL_PROJECTION;

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
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.Animator;

import uk.ac.manchester.tornado.api.collections.types.ImageByte3;

@SuppressWarnings("serial")
public class SimpleViewer extends GLCanvas implements GLEventListener {

    private static String TITLE = "Image Viewer";

    private ImageByte3 renderedImage;

    @Override
    public void init(GLAutoDrawable drawable) {
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glMatrixMode(GL_MODELVIEW);
        gl.glLoadIdentity();

        gl.glViewport(0, 0, height, width);
        gl.glMatrixMode(GL_PROJECTION);
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

        drawImageRGB(renderedImage, gl, 0, renderedImage.Y());

        gl.glFlush();

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

    public SimpleViewer(ImageByte3 image) {
        super();

        this.addGLEventListener(this);

        renderedImage = image;
    }

    public static void display(final ImageByte3 image) {

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                GLProfile glp = GLProfile.getDefault();
                GLCapabilities caps = new GLCapabilities(glp);
                caps.setDoubleBuffered(false);
                caps.setHardwareAccelerated(true);

                GLCanvas canvas = new SimpleViewer(image);
                canvas.setPreferredSize(new Dimension(image.X(), image.Y()));

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

                frame.setTitle(TITLE);
                frame.pack();
                frame.setVisible(true);
                animator.start();
            }

        });
    }

}
