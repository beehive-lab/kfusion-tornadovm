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

import static com.jogamp.opengl.fixedfunc.GLMatrixFunc.GL_MODELVIEW;
import static com.jogamp.opengl.fixedfunc.GLMatrixFunc.GL_PROJECTION;

import java.awt.Font;
import java.nio.ByteBuffer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.awt.TextRenderer;

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

    private TextRenderer paneLabelRenderer;

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

            final float zoom = config.getZoom();
            gl.glPixelZoom(zoom, -zoom);

            final int borderSize = scaled(5, zoom);
            final int x0 = borderSize;
            final int y0 = scaled(500, zoom) - borderSize;
            final int videoW = scaled(scaledVideoImage.X(), zoom);
            final int videoH = scaled(scaledVideoImage.Y(), zoom);
            final int currentW = scaled(renderedCurrentViewImage.X(), zoom);

            drawImageRGB(scaledVideoImage, gl, x0, y0);
            if (!config.drawDepth()) {
                drawImageRGBA(renderedDepthImage, gl, x0 + videoW + 2 * borderSize, y0);
            } else {
                drawImageRGB(renderedTrackingImage, gl, x0 + videoW + 2 * borderSize, y0);
            }

            drawImageRGBA(renderedCurrentViewImage, gl, x0, y0 - borderSize - videoH);
            drawImageRGBA(renderedReferenceViewImage, gl, x0 + currentW + 2 * borderSize, y0 - borderSize - videoH);
            drawImageRGBA(renderedScene, gl, (videoW * 2) + 4 * borderSize, y0);

            beginPaneLabels(drawable, zoom);
            drawPaneLabel("Camera Input", x0, y0, zoom);
            drawPaneLabel(config.drawDepth() ? "Tracking" : "Depth", x0 + videoW + 2 * borderSize, y0, zoom);
            drawPaneLabel("3D Reconstruction", (videoW * 2) + 4 * borderSize, y0, zoom);
            drawPaneLabel("Current View", x0, y0 - borderSize - videoH, zoom);
            drawPaneLabel("Reference View", x0 + currentW + 2 * borderSize, y0 - borderSize - videoH, zoom);
            endPaneLabels();

        }
        gl.glFlush();

        long stop = System.nanoTime();

        accumulatedTime += (stop - start);
        frames++;

        // Wall-clock FPS over a ~500ms sliding window, published to the config
        // so the UI can display it (for both the Java and Tornado pipelines).
        fpsWindowFrames++;
        final long elapsed = stop - fpsWindowStart;
        if (elapsed >= FPS_WINDOW_NANOS) {
            final float fps = (float) (fpsWindowFrames / (elapsed * 1e-9));
            config.setCurrentFPS(fps);
            if (config.printFPS()) {
                System.out.printf("fps: %f\n", fps);
            }
            fpsWindowStart = stop;
            fpsWindowFrames = 0;
        }
    }

    private static final long FPS_WINDOW_NANOS = 500_000_000L;
    private long fpsWindowStart = System.nanoTime();
    private long fpsWindowFrames = 0;

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

    private static final int PANE_LABEL_INSET_X = 4;
    private static final int PANE_LABEL_INSET_Y = 4;
    private static final int BASE_FONT_SIZE = 12;
    private int paneLabelFontSize = -1;

    /** Scales a base (zoom == 1) screen-space measurement by the current zoom. */
    private static int scaled(int value, float zoom) {
        return Math.round(value * zoom);
    }

    private void beginPaneLabels(GLAutoDrawable drawable, float zoom) {
        final int fontSize = Math.max(8, scaled(BASE_FONT_SIZE, zoom));
        if (paneLabelRenderer == null || fontSize != paneLabelFontSize) {
            paneLabelRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, fontSize));
            paneLabelFontSize = fontSize;
        }
        paneLabelRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        paneLabelRenderer.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * {@code x, paneTopY} is each pane's top-left corner - the same {@code x, y}
     * passed to {@link #drawImageRGB}/{@link #drawImageRGBA} for that pane.
     * The label is drawn as an inset caption just below that top edge, so it
     * always lands inside its own pane instead of in the (narrower) border
     * gap between panes, which clipped or bled into the next row down.
     */
    private void drawPaneLabel(String text, int x, int paneTopY, float zoom) {
        paneLabelRenderer.draw(text, x + scaled(PANE_LABEL_INSET_X, zoom), paneTopY - paneLabelFontSize - scaled(PANE_LABEL_INSET_Y, zoom));
    }

    private void endPaneLabels() {
        paneLabelRenderer.endRendering();
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

        gl.glViewport(0, 0, width, height);
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
