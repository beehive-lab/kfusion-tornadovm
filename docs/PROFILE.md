# Where the time goes — profiling record

Everything below is measured on an RTX 4090 (SM 8.9, driver 565.57.01), CUDA 11.5, JDK 21.0.2,
TornadoVM `5.2.1-jdk21-dev` (CUDA backend), ICL-NUIM `living_room_traj2_loop`,
`conf/bm-traj2.settings` (882 frames, 320x240 computation size, 256³ volume).

Reproduce with `scripts/profileNsys.sh conf/bm-traj2.settings 40` (nsys + NVTX) and
`--enableProfiler silent --dumpProfiler <file>` (TornadoVM profiler).

> `nsys` must profile the JVM directly. The `tornado` launcher is a Python script that `exec`s java and
> the CUDA injection does not follow that exec — profiling the launcher yields a report with **no CUDA
> data at all**. `scripts/profileNsys.sh` therefore runs `java @$TORNADOVM_HOME/tornado-argfile`.

## 1. Frame budget (882 frames)

| phase | before warm-up | with warm-up |
|---|---|---|
| acquisition | 0.27 ms | 0.29 ms |
| preprocessing (+ pyramid) | 0.96 ms | 0.32 ms |
| tracking (ICP) | 2.66 ms | 2.22 ms |
| integration | 0.14 ms | 0.08 ms |
| raycasting | 0.18 ms | 0.09 ms |
| rendering | 0.20 ms | 0.03 ms |
| **computation** | **3.92 ms (255 FPS)** | **2.70–2.82 ms (355–370 FPS)** |
| total | 4.41 ms (227 FPS) | 3.02 ms (316–331 FPS) |

The warm-up (executing every graph once before the frame loop) is bit-identical on CUDA: comparing the
two trajectory tables frame by frame gives a maximum deviation of exactly 0.

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

## 5. Second profiling round (after the warm-up), 200 frames

Kernels are unchanged in shape; the reduction still dominates GPU time:

| kernel | share | avg | instances |
|---|---|---|---|
| mapReduce | 55.0% | 34.1 µs | 2 537 |
| integrate | 11.5% | 90.0 µs | 201 |
| reduceFinal | 11.2% | 6.9 µs | 2 537 |
| trackPose | 8.6% | 5.3 µs | 2 537 |
| raycast | 5.8% | 46.0 µs | 197 |
| reducePartials | 4.4% | 2.7 µs | 2 537 |

CUDA API, per frame: **73.8 `cuStreamSynchronize`** (13.4 µs avg), **120.8 `cuMemcpyHtoDAsync`**,
63.3 `cuLaunchKernel`, ~195 CUDA events.

The NVTX ranges make the shape explicit — `:tracking` is 2.72 ms of the 2.82 ms frame, and the
per-iteration ICP copy-out shows up as its own range:

| NVTX range | share | avg | instances |
|---|---|---|---|
| `:tracking` | 28.7% | 2.72 ms | 200 |
| `:D2H 144 B` | 7.7% | **57.6 µs** | 2 537 |
| `:raycasting` | 5.8% | 0.56 ms | 197 |

**A 144-byte device→host copy costing 57.6 µs** is the signature of the problem: it is not the transfer,
it is the blocking wait for the whole graph to finish. 12.7 of those per frame is 0.73 ms.

### JVM side, 1 ms sampling (3 386 samples: 1 116 Java + 2 270 native)

| share | frame | context |
|---|---|---|
| 22.3% | `CUDAProgram.build` (native) | NVRTC compilation — one-off, now inside the warm-up |
| 19.4% | `CUDACommandQueue.enqueueRead` (native) | `streamOutBlocking` → the per-iteration ICP result copy |
| 11.7% | `DataObjectState.getDeviceBufferState` | under `TornadoVMInterpreter.executeDeAlloc` (11.5%) |
| 6.9% | `CUDACommandQueue.enqueueWrite` (native) | small H2D transfers |
| 4.5% | `CUDACommandQueue.flush` (native) | end-of-graph synchronization |
| 2.7% | `MappedByteBuffer.position` | dataset frame reader |

## 6. Conclusions for further work, in order

1. **Remove the blocking per-iteration copy-out** (19.4% of samples, 0.73 ms/frame). The 144-byte ICP
   result is copied back and waited on 12.7 times per frame. Doing the 6x6 solve and the convergence
   test on the device would remove both the copy and the host-side EJML solve, and collapse each
   level's iterations into a single plan execution.
2. **Cache the resolved object state per bytecode index in the interpreter** (11.7% of samples). Even
   after reducing `getDeviceBufferState` to one `computeIfAbsent`, the DEALLOC path re-resolves state
   for every object of every execution. Needs care around `DataObjectState.clear()`.
3. **Make CUDA graph capture survive this pipeline.** Capture now engages (see RESULTS.md for the two
   ordering rules), but replay is wrong and 50x slower, so the dispatch overhead it is meant to remove
   is still being paid.
5. **Avoid `sync_after` on inputs that are not read back.** Many of the ~74 syncs per frame follow
   small H2D copies of pose matrices; those need ordering on the stream, not host blocking.
4. Only then kernels: `mapReduce` (34 µs) can go further with a `KernelContext` warp reduction
   (`simdSum`), or by replacing the JᵗJ accumulation with cuBLAS `SSYRK` + `SGEMV` — the Jacobian
   block genuinely is an N×6 matrix product, and invalid pixels already contribute zero rows. Upper
   bound on that whole family of changes is ~0.4 ms/frame.
