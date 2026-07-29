# Where the time goes — profiling record

Everything below is measured on an RTX 4090 (SM 8.9, driver 565.57.01), CUDA 11.5, JDK 21.0.2,
TornadoVM `5.2.1-jdk21-dev` (CUDA backend), ICL-NUIM `living_room_traj2_loop`,
`conf/bm-traj2.settings` (882 frames, 320x240 computation size, 256³ volume).

Reproduce with `scripts/profileNsys.sh conf/bm-traj2.settings 40` (nsys + NVTX) and
`--enableProfiler silent --dumpProfiler <file>` (TornadoVM profiler).

> `nsys` must profile the JVM directly. The `tornado` launcher is a Python script that `exec`s java and
> the CUDA injection does not follow that exec — profiling the launcher yields a report with **no CUDA
> data at all**. `scripts/profileNsys.sh` therefore runs `java @$TORNADOVM_HOME/tornado-argfile`.

## 1. Frame budget (optimized build, 882 frames)

| phase | mean per frame |
|---|---|
| acquisition | 0.27 ms |
| preprocessing (+ pyramid) | 0.96 ms |
| tracking (ICP) | 2.66 ms |
| integration | 0.14 ms |
| raycasting | 0.18 ms |
| rendering | 0.20 ms |
| **computation** | **3.92 ms (255 FPS)** |
| total (incl. acquisition/output) | 4.41 ms (227 FPS) |

## 2. GPU kernels — `nsys` (40 frames)

Before the reduction fix, `mapReduce` was **87% of all GPU time**: 127.4 µs average over 458
instances (11.5 per frame), because it ran with `kfusion.model.reduce = 1024` threads — 32 warps on a
128-SM GPU. `trackPose`, which does the actual correspondence search over 76 800 pixels, was 5.5 µs.

After widening the reduction to 8192 threads plus two on-device collapse stages:

| kernel | share | avg | instances (40 frames) |
|---|---|---|---|
| mapReduce | 55.2% | 35.5 µs | 458 |
| integrate | 12.0% | 88.1 µs | 40 |
| reduceFinal | 10.7% | 6.9 µs | 458 |
| trackPose | 8.6% | 5.5 µs | 458 |
| raycast | 5.4% | 43.0 µs | 37 |
| reducePartials | 4.3% | 2.7 µs | 458 |
| bilateralFilter | 1.2% | 8.7 µs | 40 |
| depth2vertex / vertex2normal / resizeImage6 / mm2meters | 2.4% | 1.5–1.9 µs | 40–120 |

Total GPU kernel time ≈ **0.7 ms per frame** out of a 3.92 ms frame.

## 3. The gap is host↔device serialization, not kernels

TornadoVM profiler, 30 frames: **431 plan executions (14.4 per frame)**, mean
`TOTAL_KERNEL_TIME` = **74.9 µs per execution**, while each execution costs ≈ **230 µs** of wall time.

`nsys` CUDA API summary for the same workload (40 frames, excluding the one-off 78 ms
`cuCtxCreate`):

| CUDA API | total | calls | per frame | avg |
|---|---|---|---|---|
| `cuStreamSynchronize` | 28.96 ms | 2 715 | **68** | 10.7 µs |
| `cuMemcpyHtoDAsync` | 15.16 ms | 4 468 | **112** | 3.4 µs |
| `cuLaunchKernel` | 5.97 ms | 2 329 | 58 | 2.6 µs |
| `cuMemAlloc` | 1.48 ms | 56 | — | 26.4 µs |
| `cuEventCreate` / `Record` / `Destroy` | 3.79 ms | 21 152 | 529 | ~0.18 µs |
| `cuMemcpyDtoHAsync` | 0.89 ms | 458 | 11.5 | 1.95 µs |

So per **plan execution**: ~4.7 stream synchronizations, ~8 host-to-device copies, 4 kernel launches,
37 CUDA events. Only 56 `cuMemAlloc` calls in the whole run, so there is no allocation churn — the
cost is the *serialization*: `transfer_to_device(..., sync_after=true)` issues a
`cuStreamSynchronize` per transfer (`tornado-drivers/cuda-jni/.../CUDACommandQueue.cpp:353`), and each
graph execution ends in `clFinish` → `cuStreamSynchronize`
(`CUDACommandQueue.cpp:235`). One of those small H2D copies is the kernel stack frame, written per
launch (`CUDAInstalledCode.java:212-232`).

**This is what `withCUDAGraph()` exists to remove** — one `cuGraphLaunch` instead of ~50 driver calls
per execution. It does not currently work for this pipeline (see RESULTS.md): capturing all graphs
fails with `cuMemAlloc status=700`, capturing only the ICP graphs re-captures instead of replaying and
is 4x slower.

## 4. JVM side — JFR (`settings=profile`, full 882-frame run)

Aggregated `jdk.ExecutionSample` (77 samples; thin, so treat as a pointer, not a measurement):

| share | top-of-stack | context |
|---|---|---|
| 42.9% | `ConcurrentHashMap.get` / `containsKey` | `TornadoVMInterpreter.executeDeAlloc` → `resolveObjectState` → `DataObjectState.getDeviceBufferState` |
| 28.6% | `MappedByteBuffer.position` | `RawDevice.extractDepthFrame` (per-element depth reads) |
| 6.5% | `Arrays.fill` | `TornadoVMInterpreter.initWaitEventList` |

Two fixes were made from this and **measured**:

- `DataObjectState.getDeviceBufferState` now uses a single `computeIfAbsent` instead of
  `containsKey` + `put` + `get`.
- `TornadoVMInterpreter.executeDeAlloc` returns early when the buffer is locked (which it always is
  with the default `tornado.reuse.device.buffers=true`), skipping the batch bookkeeping and a
  `synchronized` device call per object per execution.
- `RawDevice.extractDepthFrame` bulk-copies the frame (`ShortBuffer.get(short[])`) instead of reading
  307 200 shorts one at a time through the mapped buffer.

Result: **225.6–228.6 FPS versus 225.8 FPS before** — i.e. within run-to-run noise. The JFR
attribution was misleading: those frames are cheap in wall-clock terms compared to the stream
synchronizations they sit next to. The changes are kept because they are strictly less work, but they
are *not* the lever.

## 5. Conclusions for further work, in order

1. **Make CUDA graph capture work with lazily allocated / cross-graph aliased buffers.** This is the
   only change that attacks the dominant cost (~230 µs per execution against 75 µs of kernel time,
   14.4 executions per frame).
2. **Cut the number of plan executions per frame.** The 19 ICP iterations each cost a full execution
   plus a blocking round trip for 32 floats. A device-side 6x6 solve with a device-side convergence
   flag would collapse all iterations of a level into one execution.
3. **Avoid `sync_after` on inputs that are not read back.** Several of the 68 syncs per frame are
   after small H2D copies of pose matrices; they only need ordering on the stream, not host blocking.
4. Only then kernels: `mapReduce` (35 µs) can go further with a `KernelContext` warp reduction
   (`simdSum`), or by replacing the JᵗJ accumulation with cuBLAS `SSYRK` + `SGEMV` — the Jacobian
   block genuinely is an N×6 matrix product, and invalid pixels already contribute zero rows. Upper
   bound on that whole family of changes is ~0.4 ms/frame.
