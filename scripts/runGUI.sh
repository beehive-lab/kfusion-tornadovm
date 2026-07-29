#!/bin/bash

# Run the KFusion-TornadoVM GUI on whichever backend (Metal, OpenCL, or CUDA)
# the active TornadoVM SDK provides.
#
# Usage:
#   ./scripts/runGUI.sh [backend]
#
# [backend] is optional and only needed when the active TornadoVM SDK has
# more than one backend installed together - it sets that backend's device
# priority so its devices are listed first, e.g.:
#   ./scripts/runGUI.sh opencl
#   ./scripts/runGUI.sh cuda
#   ./scripts/runGUI.sh metal

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

echo "kfusion (GUI) -Xmx20g -Xms2g kfusion.tornado.GUI"

java @"${ARGFILE}" \
    -Xmx20g -Xms2g \
    -Dtornado.kernels.coarsener=False \
    -Dtornado.enable.fix.reads=False \
    -Dtornado.compiler.fullInlining=True \
    -Dtornado.benchmarking=False \
    -Dtornado.profiler=False \
    -Dtornado.log.profiler=False \
    -Dlog4j.configurationFile="${KFUSION_ROOT}/conf/log4j2.xml" \
    "${BACKEND_PRIORITY[@]}" \
    -cp "${CLASSPATH}:${JARS}" \
    kfusion.tornado.GUI
