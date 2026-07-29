/*
 *  This file is part of Tornado-KFusion: A Java version of the KFusion computer vision
 *  algorithm running on TornadoVM.
 *  URL: https://github.com/beehive-lab/kfusion-tornadovm
 *
 *  Copyright (c) 2026, APT Group, Department of Computer Science,
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
package kfusion.tornado.common;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * Host-side NVTX ranges, so an {@code nsys} timeline carries the same phase names as the benchmark
 * table (acquisition / preprocessing / tracking / integration / raycasting / rendering).
 *
 * <p>
 * NVTX is only available on the CUDA backend and only does anything under a profiler; every method
 * here is a no-op when ranges are disabled or the device does not support them.
 */
public final class Nvtx {

    private static TornadoNativeStreamSupport device;

    private Nvtx() {
    }

    /** Enables ranges if {@code tornadoDevice} supports NVTX. Call once, at configure time. */
    public static void enable(TornadoDevice tornadoDevice) {
        device = (tornadoDevice instanceof TornadoNativeStreamSupport) ? (TornadoNativeStreamSupport) tornadoDevice : null;
    }

    public static void disable() {
        device = null;
    }

    public static boolean isEnabled() {
        return device != null;
    }

    public static void push(String name) {
        final TornadoNativeStreamSupport target = device;
        if (target != null) {
            target.nvtxRangePush(name);
        }
    }

    public static void pop() {
        final TornadoNativeStreamSupport target = device;
        if (target != null) {
            target.nvtxRangePop();
        }
    }
}
