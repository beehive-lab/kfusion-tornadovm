/*
 *  This file is part of Tornado-KFusion: A Java version of the KFusion computer vision
 *  algorithm running on TornadoVM.
 *  URL: https://github.com/beehive-lab/kfusion-tornadovm
 *
 *  Copyright (c) 2013-2019, 2024, APT Group, Department of Computer Science,
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
package kfusion.tornado.common;

import kfusion.java.common.KfusionConfig;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

public class TornadoModel extends KfusionConfig {

	private boolean useTornado;
	private TornadoDevice tornadoDevice;

	public TornadoModel() {
		super();
	}

	public boolean useTornado() {
		return useTornado;
	}

	public int getPlatformIndex() {
		return Integer.parseInt(settings.getProperty("kfusion.tornado.platform", "0"));
	}

	public int getDeviceIndex() {
		return Integer.parseInt(settings.getProperty("kfusion.tornado.device", "0"));
	}

	/**
	 * Backend to run on: CUDA (default) or OpenCL. {@code -Dkfusion.tornado.backend=OpenCL} overrides
	 * the settings file, which is how the OpenCL reference run is produced for validation.
	 */
	public String getBackendName() {
		final String property = System.getProperty("kfusion.tornado.backend");
		return (property != null) ? property : settings.getProperty("kfusion.tornado.backend", "CUDA");
	}

	public void setTornadoDevice(TornadoDevice device) {
		tornadoDevice = device;
	}

	public TornadoDevice getTornadoDevice() {
		return tornadoDevice;
	}

	public void setUseTornado(boolean value) {
		useTornado = value;
	}

	public float getMaxULP() {
		return Float.parseFloat(settings.getProperty("kfusion.maxulp", "5.0"));
	}

	public boolean printKernels() {
		return Boolean.parseBoolean(settings.getProperty("kfusion.kernels.print", "False"));
	}

	public int getReductionSize() {
		final String property = System.getProperty("kfusion.model.reduce");
		return Integer.parseInt((property != null) ? property : settings.getProperty("kfusion.model.reduce", "8192"));
	}

	/** Number of second-stage groups in the on-device ICP reduction. */
	public int getReduceGroups() {
		final String property = System.getProperty("kfusion.model.reduce.groups");
		return Integer.parseInt((property != null) ? property : settings.getProperty("kfusion.model.reduce.groups", "64"));
	}

	public boolean useSimpleReduce() {
		final String property = System.getProperty("kfusion.reduce.simple");
		return Boolean.parseBoolean((property != null) ? property : settings.getProperty("kfusion.reduce.simple", "False"));
	}

	/**
	 * ICP reduction implementation: {@code twostage} uses the KernelContext warp/block reduction (32
	 * floats copied back), {@code legacy} keeps the {@code @Parallel} map-reduce plus the host-side
	 * final sum. Overridable with {@code -Dkfusion.icp.reduce=...} for A/B runs.
	 */
	public String getIcpReduceMode() {
		final String property = System.getProperty("kfusion.icp.reduce");
		return (property != null) ? property : settings.getProperty("kfusion.icp.reduce", "twostage");
	}

	public boolean useTwoStageReduce() {
		return "twostage".equalsIgnoreCase(getIcpReduceMode());
	}

	/** Whether explicit thread-block shapes are used instead of the backend's default scheduler. */
	public boolean useGridScheduler() {
		final String property = System.getProperty("kfusion.gridscheduler");
		final String value = (property != null) ? property : settings.getProperty("kfusion.gridscheduler", "True");
		return Boolean.parseBoolean(value);
	}

	/**
	 * Which task-graphs capture and replay a CUDA graph: {@code none}, {@code icp} (the graphs replayed
	 * many times per frame) or {@code all}.
	 */
	public String getCUDAGraphScope() {
		final String property = System.getProperty("kfusion.cuda.graphs");
		final String value = (property != null) ? property : settings.getProperty("kfusion.cuda.graphs", "none");
		if ("true".equalsIgnoreCase(value)) {
			return "all";
		}
		if ("false".equalsIgnoreCase(value)) {
			return "none";
		}
		return value;
	}

	/**
	 * Stops the benchmark after this many frames. Useful to keep {@code nsys} traces small;
	 * {@code -Dkfusion.max.frames=50} overrides the settings file.
	 */
	public int getMaxFrames() {
		final String property = System.getProperty("kfusion.max.frames");
		final String value = (property != null) ? property : settings.getProperty("kfusion.max.frames", String.valueOf(Integer.MAX_VALUE));
		return Integer.parseInt(value);
	}

	/** Whether NVTX ranges are emitted around the per-frame phases (for Nsight Systems). */
	public boolean useNvtx() {
		final String property = System.getProperty("kfusion.nvtx");
		final String value = (property != null) ? property : settings.getProperty("kfusion.nvtx", "False");
		return Boolean.parseBoolean(value);
	}

	@Override
	public void reset() {
		super.reset();
		useTornado = Boolean.parseBoolean(settings.getProperty("kfusion.tornado.enable", "False"));
		tornadoDevice = selectDevice();
	}

	/**
	 * Picks the device of the requested backend ({@code kfusion.tornado.backend}, default CUDA). The
	 * legacy {@code kfusion.tornado.platform} index is only used as a fallback when the named backend
	 * is not installed.
	 */
	private TornadoDevice selectDevice() {
		final TornadoRuntime runtime = TornadoRuntimeProvider.getTornadoRuntime();
		final String requested = getBackendName();
		final int deviceIndex = getDeviceIndex();

		for (int i = 0; i < runtime.getNumBackends(); i++) {
			final TornadoBackend backend = runtime.getBackend(i);
			final TornadoVMBackendType type = backend.getBackendType();
			if (type.name().equalsIgnoreCase(requested) || backend.getName().toUpperCase().contains(requested.toUpperCase())) {
				if (deviceIndex >= backend.getNumDevices()) {
					throw new IllegalStateException(String.format("backend %s has %d device(s), but kfusion.tornado.device=%d", requested, backend.getNumDevices(), deviceIndex));
				}
				// Make this the runtime default so the execution plan does not need withDevice(): calling
				// withDevice() on a multi-graph plan re-binds each graph's buffers separately.
				runtime.setDefaultBackend(i);
				backend.setDefaultDevice(deviceIndex);
				return backend.getDevice(deviceIndex);
			}
		}

		final StringBuilder installed = new StringBuilder();
		for (int i = 0; i < runtime.getNumBackends(); i++) {
			final TornadoBackend backend = runtime.getBackend(i);
			installed.append(String.format("%n  backend %d: %s (%s), %d device(s)", i, backend.getName(), backend.getBackendType(), backend.getNumDevices()));
		}
		throw new IllegalStateException(String.format("no %s backend installed - build TornadoVM with 'make BACKEND=cuda'. Installed backends:%s", requested, installed));
	}
}
