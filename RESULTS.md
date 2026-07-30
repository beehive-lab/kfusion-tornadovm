# KFusion on TornadoVM 5.2.1, CUDA backend — results

Hardware: NVIDIA RTX 4090 (SM 8.9), driver 565.57.01, CUDA 11.5, JDK 21.0.2.
Dataset: ICL-NUIM `living_room_traj2_loop` (882 frames, 640x480 input, 320x240 computation,
256³ TSDF volume, `conf/bm-traj2.settings`).
TornadoVM: `5.2.1-jdk21-dev` built from master with `make BACKEND=cuda`.

All numbers are means over the whole sequence as printed by the benchmark; ATE is from
`bin/checkPos.py` against `livingRoom2.gt.freiburg`.

## Correctness first: the port was silently broken

Before any optimization, the pipeline had to be made correct again. On TornadoVM 5.2.1 the app as
published produces a **completely static trajectory** (ATE 1.63 m — the camera never moves) while
still reporting ~500 FPS, because every kernel was consuming stale or zero-filled data.

| Problem | Effect | Fix |
|---|---|---|
| One `TornadoExecutionPlan` per task-graph | A plan owns the device buffer pool, so images produced by one graph were invisible to the next | All graphs in a single plan, driven by `plan.withGraph(i).execute()` |
| Cross-graph data flow relied on implicit buffer sharing | Consumers re-allocated their own zeroed buffers each execution | Explicit `persistOnDevice` / `consumeFromDevice("producer", ...)` |
| `consumeFromDevice(name, ...)` only resolved its producer when that producer was the *previously executed* graph | Silently skipped for any iterative pipeline (`preproc → icp2 → icp1 → icp0`) | TornadoVM runtime fix (see below) |
| `IterativeClosestPoint.reduceValues` read `sums[jtj+1]` for the `jtj+5`, `jtj+7` and `jtj+14` accumulators | Corrupted JᵗJ → ICP diverged (ATE 3.56 m) | Corrected indices |
| `TrackingResult.getPoints()` returns 0 without an `ImageFloat8` | `RSME = sqrt(error/0) = ∞`, so `hasTracked` was never true and the pose was never applied | Explicit `points` field |
| CUDA-C has no 8-wide vector type | `trackPose` failed to compile: *"CUDA backend does not support vector width 8 (kind FLOAT8)"* | ICP result stored as a flat `FloatArray`, 8 floats per pixel |
| Sketcher inlining limit | `reduceValues` is 604 nodes vs a limit of 600 | `-Dgraal.MaximumInliningSize=1000` (note: `jdk.graal.*` is ignored by the vendored Graal) |

Validation gate used throughout: CUDA vs OpenCL, frame by frame, plus ATE.

```
$ python3 bin/compareRuns.py var/logs/ref-OpenCL.table var/logs/ref-CUDA.table --pos-tol 1e-3
euclidean: max 4.030e-04 at frame 650
VERDICT: MATCH (tolerance 1.0e-03 m)
```

## Performance

| Configuration | FPS (wall) | computation / frame | tracking / frame | ATE RMSE | ATE max |
|---|---|---|---|---|---|
| OpenCL, correct baseline | 162 | 5.79 ms | 4.76 ms | 0.019389 m | 0.044778 m |
| **CUDA, correct baseline** | **165** | **5.60 ms** | 4.29 ms | 0.019370 m | 0.044736 m |
| CUDA + explicit `GridScheduler` | 208 | 4.33 ms | — | 0.019370 m | 0.044736 m |
| CUDA + grid + wide on-device ICP reduction | 229 | 3.91 ms | 2.66 ms | 0.019406 m | 0.044764 m |
| **CUDA + all of the above + warm-up** | **316–331** | **2.70–2.82 ms** | 2.22 ms | 0.019406 m | 0.044764 m |

Net: **2.0x on computation time (5.60 → 2.82 ms), 165 → 316 FPS**, with the trajectory unchanged —
the warm-up step is **bit-identical** to the run without it (max deviation 0.000e+00 over all 882
frames), and the two optimization steps before it stay within 0.68 mm of the baseline.

### What each step did

1. **Explicit thread-block shapes** (`GridScheduler`, 1.29x, bit-identical output).
   The CUDA backend defaults to 16x16 for a 2D domain, which splits every warp across two image
   rows. `(32, 8)` keeps a warp on one contiguous row for the image kernels, `(32, 16)` for the
   radius-2 bilateral filter, and `(32, 4)` for the divergent ray-marching kernels.
2. **Wide, hierarchical ICP reduction** (further 1.10x).
   `nsys` showed `mapReduce` at **87% of all GPU time** (127 µs per launch, 11.5 launches per frame)
   because it ran with only 1024 threads — 32 warps on a 128-SM GPU. It now runs 8192 threads and is
   finished on the device by `reducePartials` (64 groups) + `reduceFinal` (32 slots), so the copy-out
   per ICP iteration is **32 floats instead of 128 KB** and the serial host-side sum is gone.
   `mapReduce` dropped from 127 µs to 35 µs per launch.

### Where the remaining time goes

Measured with the TornadoVM profiler over 30 frames: **431 plan executions (14.4 per frame)**,
**74.9 µs of kernel time per execution** but ~230 µs of wall time each. GPU kernels now account for
roughly 0.6–1.0 ms of the 3.94 ms frame; the rest is per-execution dispatch (interpreter pass, event
bookkeeping, blocking sync) plus the 19 host-side 6x6 EJML solves.

`nsys` kernel breakdown after the optimizations (40 frames):

| kernel | share | avg | instances |
|---|---|---|---|
| mapReduce | 55.2% | 35.5 µs | 458 |
| integrate | 12.0% | 88.1 µs | 40 |
| reduceFinal | 10.7% | 6.9 µs | 458 |
| trackPose | 8.6% | 5.5 µs | 458 |
| raycast | 5.4% | 43.0 µs | 37 |
| reducePartials | 4.3% | 2.7 µs | 458 |

3. **Warm-up: execute every task-graph once before the frame loop** (further 1.39x, bit-identical).
   Takes JIT compilation and the first-execution allocations out of the timed loop. Raycasting is
   deliberately excluded from the warm-up: it would overwrite the reference view with INVALID before
   the first frame, which perturbs the first tracked frames and shifts the whole trajectory (visible
   as OpenCL's ATE moving from 0.019389 m to 0.014887 m when it *is* included).

### Root cause of the remaining overhead

`nsys` CUDA API summary (40 frames) pins it down — per **plan execution**: ~4.7
`cuStreamSynchronize`, ~8 `cuMemcpyHtoDAsync`, 4 `cuLaunchKernel`, 37 CUDA events. Per frame that is
68 stream synchronizations (0.73 ms) and 112 H2D copies (0.38 ms). Only 56 `cuMemAlloc` calls occur in
the entire run, so there is no allocation churn: the cost is host↔device *serialization*, because each
transfer syncs the stream (`sync_after`) and each execution ends in `clFinish`.

Two host-side hot spots suggested by JFR were fixed and measured — `DataObjectState.getDeviceBufferState`
(single `computeIfAbsent` instead of three map ops), `executeDeAlloc` (early-out for locked buffers,
which is every buffer under the default `tornado.reuse.device.buffers=true`), and the app-side
`RawDevice` bulk depth read. Net effect: **within noise** (225.6–228.6 FPS). They are kept as strictly
less work, but they are not the lever.

**Consequence for further work:** shrinking kernels further (warp `simdSum` reductions, or replacing
the JᵗJ accumulation with cuBLAS `SSYRK` + `SGEMV` — the Jacobian block *is* an N×6 matrix product,
and invalid pixels already contribute zero rows) can buy at most ~0.4 ms/frame. The dominant cost is
the 14.4 plan executions per frame at ~230 µs each against 75 µs of kernel time. Full numbers:
[docs/PROFILE.md](docs/PROFILE.md).

### CUDA graphs

`withCUDAGraph()` is the intended answer to exactly that dispatch overhead. Getting it to engage at
all took two discoveries:

1. **Capture must be requested before a graph's first execution.** TornadoVM generates the bytecode —
   including the `CUDA_GRAPH_BEGIN_CAPTURE` … `END_CAPTURE` region — once and caches it per device
   (`TornadoTaskGraph.compileComputeGraphToTornadoVMBytecode` → `vmTable`). A `withCUDAGraph()` after
   that is silently ignored: `nsys` shows **zero `cuGraphLaunch` calls**, which is what happened in
   every configuration where capture was requested after the warm-up.
2. **The capture request must be restated for the selected graph on every execution**, because
   `withGraph(i)` re-selects it — the same pattern GPULlama3's `TornadoVMMasterPlan*` classes use.

3. **A captured graph bakes in device addresses.** Running with `-Dtornado.recover.bailout=False`
   finally shows the real error, which the default bailout was swallowing:

```
TornadoBailoutRuntimeException: Bailout is disabled.
Reason: cuGraphLaunch failed. CUresult=700
        at TornadoTaskGraph.scheduleInner(TornadoTaskGraph.java:1056)
```

That is an illegal memory access on replay, and it explains the earlier "4.5 FPS with ATE 1.54 m": the
failure was hidden and every frame fell back. The cause is that graphs consuming another graph's output
through `consumeFromDevice` have their device pointers re-assigned after capture (the reference view
does not even exist until frame 3), so the recorded nodes dereference stale addresses.

**Capture therefore works for graphs whose buffers are never re-pointed.** `preproc` qualifies - its
inputs come from the host, its outputs are its own - and capturing it is a measured win:

| capture | computation/frame (3 runs) | mean | ATE RMSE |
|---|---|---|---|
| `none` | 2.034 / 1.999 / 2.000 ms | 2.011 ms | 0.019406 m |
| **`preproc`** | 1.906 / 1.926 / 1.928 ms | **1.920 ms** | 0.019406 m |

4.5% off the frame, trajectory unchanged, and `nsys` confirms it is real: **101 `cuGraphLaunch`** calls
for 100 frames with a single `cuGraphInstantiateWithFlags`. This is now the default
(`-Dkfusion.cuda.graphs=none|preproc|all`); `all` still fails as described. After changing which
buffers cross graph boundaries, re-validate with `-Dtornado.recover.bailout=False`, because otherwise a
capture failure is silent.

## Device-side ICP solve: measured and rejected

`-Dkfusion.icp.solve=device` unrolls a level's ICP iterations into one graph and does the 6x6 solve and
the SE3 pose update in a kernel, to remove the blocking per-iteration copy-out. It is **slower**: 240
FPS / 3.88 ms against 412 FPS / 2.10 ms for the host loop, because 50 static task launches per level-0
execution cost more than the 10 round trips they replace, and a graph's task list cannot shrink when
the convergence guard fires. ATE happens to improve (0.013272 m vs 0.019406 m) since Cholesky plus a
fixed iteration count differs from the SVD pseudo-inverse with early exit. Kept behind the flag,
default `host`; a hybrid unrolling 2-3 iterations per execution is the open idea.

The solver was validated offline against the host SVD path (200 synthetic systems, worst pose-element
delta 2.03e-05, singular systems correctly deferred to the host).

## TornadoVM runtime changes required

On branch `fix/cross-graph-consume` of the TornadoVM checkout (not upstreamed):

1. `consumeFromDevice(producerName, ...)` now resolves the **named** producer from the execution plan
   instead of assuming it was the previously executed graph. Without this, any plan that revisits
   graphs (an iterative solver) silently reads a stale buffer.
   `TornadoTaskGraph.updatePersistedObjectState()` + a plan-scoped sibling registry wired through
   `TornadoExecutor` / `ImmutableTaskGraph` / `TaskGraph`.
2. `TornadoVMInterpreter.executeAlloc()` no longer dereferences a null device buffer for a persisted
   object whose producer has not run yet — it allocates it instead (the bootstrap case: ICP runs
   before the first raycast exists).

Upstream PRs that came out of profiling this application: TornadoVM #996 (the fix above), #997
(interpreter hot path), #999 (deterministic kernel source), #1000 (on-disk CUDA module cache, 2.2x
start-up), #1002 (lazy wait-event lists, **1.94x tok/s on GPULlama3 and 316 -> 424 FPS here**), #1004
(bytecode buffer sized for the graph; many-task graphs previously produced silently wrong results).

## Reproducing

```bash
# TornadoVM, CUDA backend only
cd <tornadovm> && make BACKEND=cuda
export TORNADOVM_HOME=<tornadovm>/dist/tornadovm-*-cuda-linux-amd64/tornadovm-*-cuda

# dataset (~2 GB download, converted without slambench)
export KFUSION_ROOT=$PWD
./compile.sh && ./downloadDataSets.sh

# benchmark + ATE
scripts/runBenchmark.sh CUDA conf/bm-traj2.settings my-run

# nsys timeline with NVTX phase ranges, 40 frames
scripts/profileNsys.sh conf/bm-traj2.settings 40

# frame-by-frame comparison of two runs
python3 bin/compareRuns.py var/logs/ref-OpenCL.table var/logs/my-run.table
```

Knobs: `-Dkfusion.tornado.backend=CUDA|OpenCL`, `-Dkfusion.gridscheduler=true|false`,
`-Dkfusion.icp.reduce=twostage|legacy`, `-Dkfusion.cuda.graphs=none|icp|all`,
`-Dkfusion.model.reduce=8192`, `-Dkfusion.max.frames=N`, `-Dkfusion.nvtx=true`.
