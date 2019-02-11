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
package kfusion.java.devices;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.NoSuchElementException;
import java.util.Scanner;

import kfusion.java.common.AbstractLogger;
import kfusion.java.common.KfusionConfig;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;

public class RawDevice extends AbstractLogger implements Device {
	
    final private static Float4 CAMERA = new Float4(new float[] { 481.2f, 480f, 320f, 240f });
    
    private String path;
    private boolean running;

    private RandomAccessFile file;
    private FileChannel channel;
    private MappedByteBuffer buffer;

    private final int width;
    private final int height;

    private long fileSize;
    private final int frameSize;
    private long bufferOffset;
    FileOutputStream outStream = null;
    BufferedWriter bw;
    

    public RawDevice(final String path, int width, int height) {
        this.path = path;
        this.running = false;
        this.width = width;
        this.height = height;
        frameSize = calcFrameSize();
    }

    private int calcFrameSize() {
        return (4 * 4) + ((width * height) * (2 + 3));
    }

    private boolean loadNextBuffer() {
        if (bufferOffset < fileSize) {
            final int maxNumFrames = Integer.MAX_VALUE / calcFrameSize();
            final long length = Math.min(frameSize * maxNumFrames, (fileSize - bufferOffset));

            try {
                buffer = channel.map(MapMode.READ_ONLY, bufferOffset, length);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                bufferOffset += length;

            } catch (IOException e) {
                fatal("Unable to load file %s: ", path, e.getMessage());
                buffer = null;
            }
        } else {
            buffer = null;
        }
        return buffer == null;
    }
    
    private void printHelpMessageForDataset() {
		System.out.println("Please, download with the ./downloadDataSets.sh script");
		System.exit(-1);
    }
    
    @SuppressWarnings("resource")
	private void checkDataSet() throws IOException, InterruptedException {
    	String home = System.getenv("HOME");
    	String fileName = home + "/.kfusion_tornado/" + path.split(",")[1];
		System.out.println("\t: Reading configuration file: " + fileName);
		File f = new File(fileName);
		
		if (f.exists() && !f.isDirectory()) {	
			file = new RandomAccessFile(fileName, "r");
			fileSize = file.length();
			channel = file.getChannel();
			
		} else {
			System.out.println("Data Set file does not exist. Do you want to download it automatically? (~2GB) ");
			System.out.print("Press [yes/no] (default: yes) : ");
			
			Scanner scanner = new Scanner(System.in);
			String nextLine = null; 
			try {
				nextLine = scanner.nextLine();
			} catch (NoSuchElementException e){
				printHelpMessageForDataset();
			} finally {
				scanner.close();
			}
			if (nextLine.toLowerCase().startsWith("yes")) {
				System.out.println("Downloading. This might take a while ... ");
				String cmd = "./downloadDataSets.sh " + path.split(",")[0] + " " + path.split(",")[1];
				String[] command = cmd.split(" ");
				System.out.println(cmd);
				ProcessBuilder buildProcess = new ProcessBuilder(command);
				buildProcess.redirectErrorStream(true);
				Process process = buildProcess.start();
				InputStream outputProcess = process.getInputStream();
				BufferedReader bufferReader = new BufferedReader(new InputStreamReader(outputProcess));
				String line = null;
				BufferedWriter writer = new BufferedWriter(new FileWriter("dataset.log"));
				while ((line = bufferReader.readLine()) != null) {
					System.out.println(line);
					writer.write(line);
				}
				writer.close();				
			} else {
				printHelpMessageForDataset();
			}
		}
    }

    public void init() {
        info("Initialising");

        try {
            if (file == null) {
            	if (path.startsWith("http:")) {
            		checkDataSet();
            	} else {
            		file = new RandomAccessFile(path, "r");
            		fileSize = file.length();
            		channel = file.getChannel();
            	}
            }
            bufferOffset = 0;
            loadNextBuffer();
        } catch (IOException | InterruptedException e) {
            fatal("[ERROR] Unable to load file %s: ", path, e.getMessage());
            buffer = null;
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void extractVideoFrame(ImageByte3 image) {

        if (running) {
            // buffer.getInt();
            // buffer.getInt();
            buffer.position(buffer.position() + 8);            
            final ByteBuffer bb = image.asBuffer();
            bb.position(bb.capacity());

            buffer.get(bb.array());
        }
    }
    
    @SuppressWarnings("unused")
	private void writeFrameToFile(ImageFloat image) {

        if (running) {
        
            buffer.position(buffer.position() + 8);
            final ShortBuffer sb = buffer.asShortBuffer();
            
            if (outStream == null)  {
            	try {
            		outStream = new FileOutputStream("/tmp/output.txt");
            		OutputStreamWriter w = new OutputStreamWriter(outStream, "UTF-8");
            		bw = new BufferedWriter(w);
            	} catch (IOException e) {
            		e.printStackTrace();
            	}
            }

            for (int i = 0; i < width * height; i++) {
                final float value = sb.get();
                try {
					bw.write(value  + ",");
				} catch (IOException e) {
					e.printStackTrace();
				}
                if (value < 1.0) { 
                	System.out.println("VALUE !!1: " + value);
                }
                image.set(i, (value > 0f) ? value : -value);
            }   
            try {
				bw.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
            buffer.position(buffer.position() + (width * height * 2));

        }
    }

    private void extractDepthFrame(ImageFloat image) {
        if (running) {
            buffer.position(buffer.position() + 8);
            final ShortBuffer sb = buffer.asShortBuffer();
           
            for (int i = 0; i < width * height; i++) {
                final float value = sb.get();
                image.set(i, (value > 0f) ? value : -value);
            }   
            buffer.position(buffer.position() + (width * height * 2));
        }
    }

    public void skipVideoFrame() {
        if (buffer.hasRemaining() && running) {
            buffer.position(buffer.position() + 8 + (width * height * 3));
        }
    }

    @Override
    public boolean pollVideo(ImageByte3 image) {
        boolean haveFrame = true;

        if (buffer.hasRemaining()) {
            extractVideoFrame(image);
        } else {
            haveFrame = false;
            running = false;
        }

        return haveFrame;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public boolean pollDepth(ImageFloat image) {
        boolean haveFrame = true;

        if (buffer.hasRemaining()) {
            extractDepthFrame(image);
        } else if (bufferOffset < fileSize) {
            loadNextBuffer();
            extractDepthFrame(image);
        } else {
            haveFrame = false;
            running = false;
        }

        return haveFrame;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void shutdown() {
        try {
            file.close();
            channel.close();
            buffer = null;
            file = null;
        } catch (IOException e) {
            warn("Unable to close file %s: %s", path, e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public <T extends KfusionConfig> void updateModel(T config) {
        config.setCamera(CAMERA);

        final Float3 initPositions = new Float3(0.34f, 0.5f, 0.24f);
        config.setInitialPositionFactors(initPositions);
        config.setFarPlane(4f);

    }

    @Override
    public boolean hasReferencePose() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Float3 getTranslation() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Float4 getRotation() {
        // TODO Auto-generated method stub
        return null;
    }

    public String toString() {
        return String.format("Raw Reader <%d x %d>: %s", width, height, path.substring(path.lastIndexOf("/") + 1));
    }

}
