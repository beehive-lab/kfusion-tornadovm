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
package kfusion.java.algorithms;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat8;
import uk.ac.manchester.tornado.api.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.api.data.nativetypes.FloatArray;

public class TrackingResult {

	public float getTracked() {
		return tracked;
	}

	public float getTooFar() {
		return tooFar;
	}

	public float getWrongNormal() {
		return wrongNormal;
	}

	public float getOther() {
		return other;
	}

	public float getError() {
		return error;
	}

	public float tracked;
	public float tooFar;
	public float wrongNormal;
	public float other;
	public final Matrix4x4Float pose;
	public  ImageFloat8	resultImage;
	public final FloatArray x;
	public float error;

	public TrackingResult(){
		pose = new Matrix4x4Float();
		x = new FloatArray(6);//Float6();
		x.clear();
		tracked = 0;
		tooFar = 0;
		wrongNormal = 0;
		other = 0;
	}

	private static float length(FloatArray value) {
		return TornadoMath.sqrt(dot(value, value));
	}

	public static float dot(FloatArray a, FloatArray b) {
		float result = 0f;
		final FloatArray m = mult(a, b);
		for (int i = 0; i < a.getSize(); i++) {
			result += m.get(i);
		}
		return result;
	}

	public static FloatArray mult(FloatArray a, FloatArray b) {
		final FloatArray result = new FloatArray(6);
		for (int i = 0; i < result.getSize(); i++) {
			result.set(i, a.get(i) * b.get(i));
		}
		return result;
	}

	public String toString(){
		if(resultImage == null)
			return "invalid results";
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Tracking Result: ok=%.2f %%, too far=%.2f %%, wrong normal=%.2f %%, other=%.2f %%\n",getPercent(tracked),getPercent(tooFar),getPercent(wrongNormal),getPercent(other)));
		sb.append(String.format("               : points=%.0f\n",getPoints()));
		sb.append(String.format("               : RSME=%e\n",getRSME()));
		sb.append(String.format("               : x.norm=%e\n",length(x)));
		sb.append(String.format("               : x=%s\n",stringRepresentation(x)));
		sb.append(String.format("               : pose\n%s\n",pose.toString()));
		return sb.toString();
	}

	private static String stringRepresentation(FloatArray x) {
		String values = "";
		for (int i = 0; i < x.getSize(); i++) {
			values = values + " " + x.get(i);
		}
		return values;
	}

	public double getPoints(){
		if(resultImage == null)
			return 0;

		return resultImage.X() * resultImage.Y();
	}
	private double getPercent(float value){
		return ((double) value) / (getPoints()) * 100.0;
	}


	public double getRSME() {
		return Math.sqrt(error / getPoints());
	}

	public double getTracked(double points) {
		return ((double) tracked) / points;
	}


	public Matrix4x4Float getPose() {
		return pose;
	}

	public FloatArray getX() {
		return x;
	}

	public ImageFloat8 getImage(){
		return resultImage;
	}

	public ImageFloat8 getResultImage() {
		return resultImage;
	}

	public void setResultImage(ImageFloat8 resultImage) {
		this.resultImage = resultImage;
	}
}
