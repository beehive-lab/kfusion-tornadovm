#!/bin/bash

# Run the KFusion-TornadoVM console/Benchmark mode on whichever backend
# (Metal, OpenCL, or CUDA) the active TornadoVM SDK provides.
#
# Usage:
#   ./scripts/run.sh [backend] [settings-file]
#
# [backend] is optional and only needed when the active TornadoVM SDK has
# more than one backend installed together - it sets that backend's device
# priority so its devices are listed first, e.g.:
#   ./scripts/run.sh opencl
#   ./scripts/run.sh cuda
#   ./scripts/run.sh metal
#
# [settings-file] defaults to conf/bm-traj2.settings, e.g.:
#   ./scripts/run.sh metal conf/bm-traj3.settings

: "${TORNADOVM_HOME:?TORNADOVM_HOME is not set. Install a TornadoVM SDK (Metal, OpenCL, or CUDA backend) and source its setvars.sh first.}"

ARGFILE="${TORNADOVM_HOME}/tornado-argfile"
if [ ! -f "${ARGFILE}" ]; then
    echo "Generating TornadoVM argfile for ${TORNADOVM_HOME}..."
    tornado --generate-argfile
fi

JARS=$(echo ${KFUSION_ROOT}/target/*.jar | tr ' ' ':')

BACKEND_PRIORITY=()
if [ -n "$1" ]; then
    BACKEND_PRIORITY=("-Dtornado.$1.priority=100")
fi

SETTINGS="${2:-conf/bm-traj2.settings}"

echo "kfusion kfusion.tornado.Benchmark ${SETTINGS}"

java @"${ARGFILE}" \
    -Xms2G -Xmx20G \
    -Dtornado.kernels.coarsener=False \
    -Dtornado.enable.fix.reads=False \
    -Dtornado.compiler.fullInlining=True \
    -Dtornado.benchmarking=False \
    -Dtornado.profiler=False \
    -Dtornado.log.profiler=False \
    -Dlog4j.configurationFile="${KFUSION_ROOT}/conf/log4j2.xml" \
    "${BACKEND_PRIORITY[@]}" \
    -cp "${CLASSPATH}:${JARS}" \
    kfusion.tornado.Benchmark "${SETTINGS}"
