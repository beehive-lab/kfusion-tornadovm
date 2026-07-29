/*
 *  This file is part of Tornado-KFusion: A Java version of the KFusion computer vision
 *  algorithm running on TornadoVM.
 *  URL: https://github.com/beehive-lab/kfusion-tornadovm
 *
 *  Copyright (c) 2013-2019, 2024, 2026, APT Group, Department of Computer Science,
 *  The University of Manchester
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
package kfusion.tools;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.IImageLine;
import ar.com.hjg.pngj.PngReader;

/**
 * Converts an ICL-NUIM (Handa et al.) synthetic trajectory into the flat KFusion {@code .raw} format
 * read by {@link kfusion.java.devices.RawDevice}, without needing slambench's {@code scene2raw}.
 *
 * <p>
 * Input is either the downloaded {@code living_room_trajN_loop.tgz} (streamed, nothing is extracted
 * to disk) or an already extracted directory containing {@code scene_00_%04d.depth} (ASCII depth in
 * metres, along the ray) and {@code scene_00_%04d.png} (RGB).
 *
 * <p>
 * Per frame the output holds, little-endian: {@code int width, int height, uint16 depth[w*h]} in
 * millimetres, followed by {@code int width, int height, uint8 rgb[w*h*3]}.
 *
 * <p>
 * Radial (along-ray) depth is converted to planar depth with the same expression slambench uses in
 * {@code framework/tools/dataset-tools/ICLNUIM.cpp}:
 * {@code d_planar = d / sqrt(((u-u0)/fx)^2 + ((v-v0)/fy)^2 + 1)}.
 */
public class Scene2Raw {

    private static final float U0 = 319.50f;
    private static final float V0 = 239.50f;
    private static final float FX = 481.20f;
    private static final float FY = -480.00f;

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private static final String DEPTH_SUFFIX = ".depth";
    private static final String VIDEO_SUFFIX = ".png";

    private final int width;
    private final int height;
    private final float[] radialToPlanar;

    public Scene2Raw(int width, int height) {
        this.width = width;
        this.height = height;
        this.radialToPlanar = new float[width * height];
        for (int v = 0; v < height; v++) {
            for (int u = 0; u < width; u++) {
                final double du = (u - U0) / FX;
                final double dv = (v - V0) / FY;
                radialToPlanar[u + v * width] = (float) (1.0 / Math.sqrt(du * du + dv * dv + 1.0));
            }
        }
    }

    /** One converted frame, ready to be appended to the output file. */
    private static final class Frame {
        final int index;
        final short[] depth;
        final byte[] rgb;

        Frame(int index, short[] depth, byte[] rgb) {
            this.index = index;
            this.depth = depth;
            this.rgb = rgb;
        }
    }

    /** Raw bytes of the two files belonging to one frame. */
    private static final class RawFrame {
        int index = -1;
        byte[] depth;
        byte[] png;

        boolean isComplete() {
            return depth != null && png != null;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: Scene2Raw <living_room_trajN_loop.tgz | extracted-dir> <output.raw> [width height]");
            System.exit(1);
        }

        final int width = (args.length >= 4) ? Integer.parseInt(args[2]) : DEFAULT_WIDTH;
        final int height = (args.length >= 4) ? Integer.parseInt(args[3]) : DEFAULT_HEIGHT;

        final File input = new File(args[0]);
        final File output = new File(args[1]);
        if (!input.exists()) {
            System.err.println("input does not exist: " + input);
            System.exit(1);
        }

        final Scene2Raw converter = new Scene2Raw(width, height);
        final long start = System.nanoTime();
        final int frames;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(output), 8 << 20)) {
            frames = input.isDirectory() ? converter.convertDirectory(input, out) : converter.convertTarball(input, out);
        }
        final double elapsed = (System.nanoTime() - start) * 1e-9;

        final long expected = (long) frames * (16L + (long) width * height * 5L);
        System.out.printf("%nWrote %d frames (%d x %d) to %s in %.1f s%n", frames, width, height, output, elapsed);
        System.out.printf("File size %d bytes, expected %d bytes: %s%n", output.length(), expected, output.length() == expected ? "OK" : "MISMATCH");
        if (output.length() != expected) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Drivers
    // ------------------------------------------------------------------------------------------

    private int convertTarball(File tarball, OutputStream out) throws Exception {
        try (InputStream in = new GZIPInputStream(new FileInputStream(tarball), 1 << 20)) {
            final TarStream tar = new TarStream(in);
            return convert(new FrameSource() {
                @Override
                public RawFrame next() throws IOException {
                    final RawFrame frame = new RawFrame();
                    TarStream.Entry entry;
                    while ((entry = tar.nextEntry()) != null) {
                        final boolean isDepth = entry.name.endsWith(DEPTH_SUFFIX);
                        final boolean isVideo = entry.name.endsWith(VIDEO_SUFFIX);
                        if (!isDepth && !isVideo) {
                            continue; // camera .txt files and anything else
                        }
                        final int index = frameIndex(entry.name);
                        if (index < 0) {
                            continue;
                        }
                        if (frame.index >= 0 && index != frame.index) {
                            throw new IOException("interleaved frames in tarball at " + entry.name);
                        }
                        frame.index = index;
                        if (isDepth) {
                            frame.depth = tar.readEntry(entry);
                        } else {
                            frame.png = tar.readEntry(entry);
                        }
                        if (frame.isComplete()) {
                            return frame;
                        }
                    }
                    return null;
                }
            }, out);
        }
    }

    private int convertDirectory(File dir, OutputStream out) throws Exception {
        return convert(new FrameSource() {
            private int next = 0;

            @Override
            public RawFrame next() throws IOException {
                final File depth = new File(dir, String.format("scene_00_%04d%s", next, DEPTH_SUFFIX));
                final File png = new File(dir, String.format("scene_00_%04d%s", next, VIDEO_SUFFIX));
                if (!depth.exists() || !png.exists()) {
                    return null;
                }
                final RawFrame frame = new RawFrame();
                frame.index = next++;
                frame.depth = Files.readAllBytes(depth.toPath());
                frame.png = Files.readAllBytes(png.toPath());
                return frame;
            }
        }, out);
    }

    private interface FrameSource {
        RawFrame next() throws IOException;
    }

    /**
     * Reads frames sequentially, decodes them on a thread pool, and writes them in frame order.
     */
    private int convert(FrameSource source, OutputStream out) throws IOException, InterruptedException, ExecutionException {
        final int threads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final ArrayDeque<Future<Frame>> pending = new ArrayDeque<>();
        int written = 0;
        try {
            RawFrame raw;
            while ((raw = source.next()) != null) {
                final RawFrame current = raw;
                pending.add(pool.submit(new Callable<Frame>() {
                    @Override
                    public Frame call() throws Exception {
                        return decode(current);
                    }
                }));
                while (pending.size() >= threads * 2) {
                    written = write(out, pending.poll().get(), written);
                }
            }
            while (!pending.isEmpty()) {
                written = write(out, pending.poll().get(), written);
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.println();
        return written;
    }

    private int write(OutputStream out, Frame frame, int written) throws IOException {
        if (frame.index != written) {
            throw new IOException("frames out of order: expected " + written + " got " + frame.index);
        }
        final ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(width).putInt(height);
        out.write(header.array());

        final ByteBuffer depth = ByteBuffer.allocate(frame.depth.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        depth.asShortBuffer().put(frame.depth);
        out.write(depth.array());

        out.write(header.array());
        out.write(frame.rgb);

        final int count = written + 1;
        if (count % 50 == 0) {
            System.out.printf("\rconverted %d frames", count);
            System.out.flush();
        }
        return count;
    }

    // ------------------------------------------------------------------------------------------
    // Per-frame decoding
    // ------------------------------------------------------------------------------------------

    private Frame decode(RawFrame raw) throws IOException {
        return new Frame(raw.index, parseDepth(raw.depth), readPng(raw.png));
    }

    /**
     * Parses the ASCII depth map (metres, along the ray) into planar millimetres.
     */
    private short[] parseDepth(byte[] ascii) throws IOException {
        final int pixels = width * height;
        final short[] depth = new short[pixels];
        int index = 0;
        int position = 0;
        final int length = ascii.length;
        while (position < length && index < pixels) {
            while (position < length && isSpace(ascii[position])) {
                position++;
            }
            final int tokenStart = position;
            while (position < length && !isSpace(ascii[position])) {
                position++;
            }
            if (position == tokenStart) {
                break;
            }
            final float metres = parseFloat(ascii, tokenStart, position);
            final int millimetres = Math.round(metres * 1000f * radialToPlanar[index]);
            depth[index++] = (short) Math.min(millimetres, 0xFFFF);
        }
        if (index != pixels) {
            throw new IOException("expected " + pixels + " depth samples, found " + index);
        }
        return depth;
    }

    private static boolean isSpace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }

    /**
     * Allocation-free parser for the plain {@code [-]ddd.ddd} tokens the dataset uses; anything more
     * exotic (exponents, NaN) falls back to {@link Float#parseFloat}.
     */
    private static float parseFloat(byte[] data, int from, int to) {
        long mantissa = 0;
        int scale = 0;
        boolean negative = false;
        boolean seenDot = false;
        int position = from;
        if (position < to && (data[position] == '-' || data[position] == '+')) {
            negative = data[position] == '-';
            position++;
        }
        for (; position < to; position++) {
            final byte c = data[position];
            if (c == '.') {
                if (seenDot) {
                    return Float.parseFloat(new String(data, from, to - from));
                }
                seenDot = true;
            } else if (c >= '0' && c <= '9') {
                mantissa = mantissa * 10 + (c - '0');
                if (seenDot) {
                    scale++;
                }
            } else {
                return Float.parseFloat(new String(data, from, to - from));
            }
        }
        final double value = mantissa / POWERS_OF_TEN[scale];
        return (float) (negative ? -value : value);
    }

    private static final double[] POWERS_OF_TEN = { 1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18 };

    /** Decodes the RGB PNG into a tightly packed {@code rgb[w*h*3]} array. */
    private byte[] readPng(byte[] png) throws IOException {
        final PngReader reader = new PngReader(new java.io.ByteArrayInputStream(png));
        try {
            if (reader.imgInfo.cols != width || reader.imgInfo.rows != height) {
                throw new IOException("unexpected PNG size " + reader.imgInfo.cols + "x" + reader.imgInfo.rows);
            }
            final int channels = reader.imgInfo.channels;
            final byte[] rgb = new byte[width * height * 3];
            for (int row = 0; row < height; row++) {
                final IImageLine line = reader.readRow();
                final int offset = row * width * 3;
                if (line instanceof ImageLineByte) {
                    final byte[] scanline = ((ImageLineByte) line).getScanline();
                    copyChannels(scanline, channels, rgb, offset);
                } else {
                    final int[] scanline = ((ImageLineInt) line).getScanline();
                    copyChannels(scanline, channels, rgb, offset, reader.imgInfo.bitDepth);
                }
            }
            reader.end();
            return rgb;
        } finally {
            reader.close();
        }
    }

    private void copyChannels(byte[] scanline, int channels, byte[] rgb, int offset) {
        for (int col = 0; col < width; col++) {
            final int source = col * channels;
            if (channels >= 3) {
                rgb[offset + col * 3] = scanline[source];
                rgb[offset + col * 3 + 1] = scanline[source + 1];
                rgb[offset + col * 3 + 2] = scanline[source + 2];
            } else {
                final byte grey = scanline[source];
                rgb[offset + col * 3] = grey;
                rgb[offset + col * 3 + 1] = grey;
                rgb[offset + col * 3 + 2] = grey;
            }
        }
    }

    private void copyChannels(int[] scanline, int channels, byte[] rgb, int offset, int bitDepth) {
        final int shift = (bitDepth == 16) ? 8 : 0;
        for (int col = 0; col < width; col++) {
            final int source = col * channels;
            if (channels >= 3) {
                rgb[offset + col * 3] = (byte) (scanline[source] >> shift);
                rgb[offset + col * 3 + 1] = (byte) (scanline[source + 1] >> shift);
                rgb[offset + col * 3 + 2] = (byte) (scanline[source + 2] >> shift);
            } else {
                final byte grey = (byte) (scanline[source] >> shift);
                rgb[offset + col * 3] = grey;
                rgb[offset + col * 3 + 1] = grey;
                rgb[offset + col * 3 + 2] = grey;
            }
        }
    }

    private static int frameIndex(String name) {
        final int slash = name.lastIndexOf('/');
        final String base = (slash < 0) ? name : name.substring(slash + 1);
        if (!base.startsWith("scene_00_")) {
            return -1;
        }
        final int dot = base.lastIndexOf('.');
        if (dot < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(base.substring("scene_00_".length(), dot));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Minimal streaming tar reader (ustar, regular files only)
    // ------------------------------------------------------------------------------------------

    private static final class TarStream {
        private static final int BLOCK = 512;

        private final InputStream in;
        private final byte[] header = new byte[BLOCK];
        private long remaining;

        TarStream(InputStream in) {
            this.in = in;
        }

        static final class Entry {
            final String name;
            final long size;

            Entry(String name, long size) {
                this.name = name;
                this.size = size;
            }
        }

        Entry nextEntry() throws IOException {
            skipRemaining();
            while (true) {
                if (!readFully(header, BLOCK)) {
                    return null;
                }
                if (isZeroBlock()) {
                    return null;
                }
                final String name = readString(0, 100);
                final long size = readOctal(124, 12);
                final char type = (char) (header[156] == 0 ? '0' : header[156]);
                remaining = size;
                if (type == '0' || type == 0 || type == '7') {
                    return new Entry(name, size);
                }
                // directories, links, pax headers: skip the payload and continue
                skipRemaining();
            }
        }

        byte[] readEntry(Entry entry) throws IOException {
            final byte[] data = new byte[(int) entry.size];
            if (!readFully(data, data.length)) {
                throw new EOFException("truncated tar entry " + entry.name);
            }
            remaining = 0;
            skipPadding(entry.size);
            return data;
        }

        private void skipRemaining() throws IOException {
            if (remaining > 0) {
                skipExactly(remaining);
                final long size = remaining;
                remaining = 0;
                skipPadding(size);
            }
        }

        private void skipPadding(long size) throws IOException {
            final long padding = (BLOCK - (size % BLOCK)) % BLOCK;
            if (padding > 0) {
                skipExactly(padding);
            }
        }

        private void skipExactly(long count) throws IOException {
            long left = count;
            final byte[] sink = new byte[8192];
            while (left > 0) {
                final int read = in.read(sink, 0, (int) Math.min(sink.length, left));
                if (read < 0) {
                    throw new EOFException("unexpected end of tar stream");
                }
                left -= read;
            }
        }

        private boolean readFully(byte[] buffer, int length) throws IOException {
            int offset = 0;
            while (offset < length) {
                final int read = in.read(buffer, offset, length - offset);
                if (read < 0) {
                    if (offset == 0) {
                        return false; // clean end of stream
                    }
                    throw new EOFException("truncated tar stream");
                }
                offset += read;
            }
            return true;
        }

        private boolean isZeroBlock() {
            for (int i = 0; i < BLOCK; i++) {
                if (header[i] != 0) {
                    return false;
                }
            }
            return true;
        }

        private String readString(int offset, int length) {
            int end = offset;
            while (end < offset + length && header[end] != 0) {
                end++;
            }
            return new String(header, offset, end - offset);
        }

        private long readOctal(int offset, int length) {
            long value = 0;
            for (int i = offset; i < offset + length; i++) {
                final byte c = header[i];
                if (c == 0 || c == ' ') {
                    if (value != 0) {
                        break;
                    }
                    continue;
                }
                value = value * 8 + (c - '0');
            }
            return value;
        }
    }
}
