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

import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;

public class Utils {

    public static void loadData(String file, float[] dst) throws Exception {

        FileInputStream fis = new FileInputStream(file);
        FileChannel vChannel = fis.getChannel();

        ByteBuffer bb = ByteBuffer.allocate((int) vChannel.size());
        bb.order(ByteOrder.LITTLE_ENDIAN);

        vChannel.read(bb);
        bb.flip();

        vChannel.close();

        FloatBuffer fb = bb.asFloatBuffer();
        fb.get(dst);

        fis.close();
    }

    public static void dumpData(String file, PrimitiveStorage<FloatBuffer> src) throws Exception {

        FileOutputStream fis = new FileOutputStream(file);

        FileChannel vChannel = fis.getChannel();
        vChannel.position(0);

        final ByteBuffer bb = ByteBuffer.allocate(src.size() * 4);
        bb.asFloatBuffer().put(src.asBuffer());

        bb.order(ByteOrder.LITTLE_ENDIAN);

        vChannel.write(bb);

        vChannel.close();

        fis.close();
    }

    public static void loadData(String file, FloatBuffer dst) throws Exception {

        final FileInputStream fis = new FileInputStream(file);
        final FileChannel vChannel = fis.getChannel();

        final ByteBuffer bb = ByteBuffer.allocate((int) vChannel.size());
        bb.order(ByteOrder.LITTLE_ENDIAN);

        vChannel.read(bb);

        // bb.rewind();
        bb.flip();

        vChannel.close();

        if (bb.asFloatBuffer().capacity() != dst.capacity())
            System.err.printf("buffers are not the same size: %d != %d (%s)\n", bb.asFloatBuffer().capacity(), dst.capacity(), file);

        dst.put(bb.asFloatBuffer());

        fis.close();
    }

    public static void loadData(String file, ShortBuffer dst) throws Exception {

        final FileInputStream fis = new FileInputStream(file);
        final FileChannel vChannel = fis.getChannel();

        final ByteBuffer bb = ByteBuffer.allocate((int) vChannel.size());
        bb.order(ByteOrder.LITTLE_ENDIAN);

        vChannel.read(bb);

        // bb.rewind();
        bb.flip();

        vChannel.close();

        dst.put(bb.asShortBuffer());

        fis.close();
    }

    public static void loadData(String file, byte[] dst) throws Exception {

        FileInputStream fis = new FileInputStream(file);
        FileChannel vChannel = fis.getChannel();

        ByteBuffer bb = ByteBuffer.allocate((int) vChannel.size());
        bb.order(ByteOrder.LITTLE_ENDIAN);

        vChannel.read(bb);
        // bb.rewind();
        bb.flip();

        vChannel.close();

        bb.get(dst);

        fis.close();
    }

    public static void dumpData(String file, float[] src) throws Exception {

        FileOutputStream fis = new FileOutputStream(file);
        FileChannel vChannel = fis.getChannel();

        ByteBuffer bb = ByteBuffer.allocate(src.length * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(src);
        fb.flip();

        bb.rewind();

        vChannel.write(bb);

        // bb.rewind();
        // bb.flip();

        vChannel.close();

        fis.close();
    }

    public static void dumpData(String file, short[] src) throws Exception {

        FileOutputStream fis = new FileOutputStream(file);
        FileChannel vChannel = fis.getChannel();

        ByteBuffer bb = ByteBuffer.allocate(src.length * 2);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        ShortBuffer sb = bb.asShortBuffer();
        sb.put(src);
        sb.flip();

        bb.rewind();

        vChannel.write(bb);

        // bb.rewind();
        // bb.flip();

        vChannel.close();

        fis.close();
    }

    public static void loadData(String file, short[] dst) throws Exception {

        FileInputStream fis = new FileInputStream(file);
        FileChannel vChannel = fis.getChannel();

        ByteBuffer bb = ByteBuffer.allocate((int) vChannel.size());
        bb.order(ByteOrder.LITTLE_ENDIAN);

        vChannel.read(bb);
        // bb.rewind();
        bb.flip();

        vChannel.close();

        ShortBuffer fb = bb.asShortBuffer();
        fb.get(dst);

        fis.close();
    }

}
