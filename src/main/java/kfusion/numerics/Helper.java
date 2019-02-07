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
package kfusion.numerics;

import javax.swing.JTextField;

import uk.ac.manchester.tornado.api.collections.types.Float4;

public class Helper {

    public static final float sq(float x) {
        return x * x;
    }

    public static final byte sq(byte x) {
        return (byte) (x * x);
    }

    public static final float rsqrt(float x) {
        return (float) (1f / Math.sqrt(x));
    }

    public static final Float4 scaleCameraConfig(Float4 camera, int scale) {
        if (scale > 1) {
            return Float4.div(camera, scale);
        }
        return camera;
    }

    public static final float parseFloatValue(final JTextField textField, float currentValue) {
        float result = currentValue;
        try {
            result = Float.parseFloat(textField.getText());
        } catch (NumberFormatException e) {
            System.err.printf("Invalid value: %s\n", textField.getText());
        }
        return result;
    }

    public static final int parseIntValue(final JTextField textField, int currentValue) {
        int result = currentValue;
        try {
            result = Integer.parseInt(textField.getText());
        } catch (NumberFormatException e) {
            System.err.printf("Invalid value: %s\n", textField.getText());
        }
        return result;
    }

}
