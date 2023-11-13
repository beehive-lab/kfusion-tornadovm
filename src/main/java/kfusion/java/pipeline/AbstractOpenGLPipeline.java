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
package kfusion.java.pipeline;

import static javax.media.opengl.fixedfunc.GLMatrixFunc.GL_MODELVIEW;
import static javax.media.opengl.fixedfunc.GLMatrixFunc.GL_PROJECTION;

import java.nio.ByteBuffer;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;

import kfusion.java.common.KfusionConfig;
import kfusion.tornado.algorithms.Renderer;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageByte4;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.matrix.MatrixMath;

public abstract class AbstractOpenGLPipeline<T extends KfusionConfig> extends AbstractPipeline<T> implements GLEventListener {

    public AbstractOpenGLPipeline(T config) {
        super(config);
    }

    @Override
    public void display(GLAutoDrawable drawable) {

        long start = System.nanoTime();

        if (config.getQuit()) {
            quit();
        }

        final GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT);

        if (config.getDevice() != null) {

            if (config.getAndClearReset()) {
                reset();
            }

            updateUserPose();

            execute();

            final int borderSize = 5;
            final int x0 = borderSize;
            final int y0 = 500 - borderSize;

            drawImageRGB(scaledVideoImage, gl, x0, y0);
            if (!config.drawDepth()) {
                drawImageRGBA(renderedDepthImage, gl, x0 + scaledVideoImage.X() + 2 * borderSize, y0);
            } else {
                drawImageRGB(renderedTrackingImage, gl, x0 + scaledVideoImage.X() + 2 * borderSize, y0);
            }

            drawImageRGBA(renderedCurrentViewImage, gl, x0, y0 - borderSize - scaledVideoImage.Y());
            drawImageRGBA(renderedReferenceViewImage, gl, x0 + renderedCurrentViewImage.X() + 2 * borderSize, y0 - borderSize - scaledVideoImage.Y());
            drawImageRGBA(renderedScene, gl, (scaledVideoImage.X() * 2) + 4 * borderSize, y0);

        }
        gl.glFlush();

        long stop = System.nanoTime();

        accumulatedTime += (stop - start);
        frames++;

        if (config.printFPS() && frames % statsRate == 0) {
            double fps = ((double) statsRate) / (((double) accumulatedTime) * 1e-9);
            System.out.printf("fps: %f\n", fps);
            accumulatedTime = 0;
        }
    }

    private void drawImageRGB(ImageByte3 image, final GL2 gl, int x, int y) {
        final ByteBuffer bb = image.asBuffer();
        bb.rewind();
        gl.glWindowPos2i(x, y);
        gl.glDrawPixels(image.X(), image.Y(), GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, bb);
    }

    private void drawImageRGBA(ImageByte4 image, final GL2 gl, int x, int y) {
        final ByteBuffer bb = image.asBuffer();
        bb.rewind();
        gl.glWindowPos2i(x, y);
        gl.glDrawPixels(image.X(), image.Y(), GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, bb);
    }

    @Override
    public void dispose(GLAutoDrawable arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public void init(GLAutoDrawable arg0) {
        // TODO Auto-generated method stub
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
    public void renderScene() {
        final Matrix4x4Float scenePose = sceneView.getPose();
        final Matrix4x4Float tmp = new Matrix4x4Float();
        final Matrix4x4Float tmp2 = new Matrix4x4Float();

        if (config.getAndClearRotateNegativeX()) {
            updateRotation(rot, config.getUptrans());
        }

        if (config.getAndClearRotatePositiveX()) {
            updateRotation(rot, config.getDowntrans());
        }

        if (config.getAndClearRotatePositiveY()) {
            updateRotation(rot, config.getRighttrans());
        }

        if (config.getAndClearRotateNegativeY()) {
            updateRotation(rot, config.getLefttrans());
        }

        MatrixMath.sgemm(trans, rot, tmp);
        MatrixMath.sgemm(tmp, preTrans, tmp2);
        MatrixMath.sgemm(tmp2, invK, scenePose);

        Renderer.renderVolume(renderedScene, volume, volumeDims, scenePose, nearPlane, farPlane * 2f, smallStep, largeStep, light, ambient);
    }
}
