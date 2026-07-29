#!/bin/bash
#
# Profiles the KFusion CUDA pipeline with Nsight Systems, with NVTX ranges per per-frame phase and
# per plan execution, plus the TornadoVM profiler for per-kernel times.
#
# usage: scripts/profileNsys.sh [settings-file] [frames]
#   defaults: conf/bm-traj2.settings  50 frames (keep the trace small)
#
# Environment:
#   TORNADOVM_HOME   TornadoVM SDK built with 'make BACKEND=cuda'
#   KFUSION_ROOT     kfusion checkout (default: parent of this script)
#   NSYS_EXTRA       extra nsys flags
#   KFUSION_JFLAGS   extra JVM flags (e.g. -Dkfusion.cuda.graphs=False)

set -e

KFUSION_ROOT=${KFUSION_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}
SETTINGS=${1:-conf/bm-traj2.settings}
FRAMES=${2:-50}
STAMP=$(date '+%Y-%m-%d-%H%M%S')
OUT_DIR=${KFUSION_ROOT}/var/nsys
REPORT=${OUT_DIR}/kfusion-$(basename ${SETTINGS} .settings)-${STAMP}

if [ -z "${TORNADOVM_HOME}" ]; then
    echo "TORNADOVM_HOME is not set (point it at the CUDA SDK build)" >&2
    exit 1
fi
if ! command -v nsys > /dev/null; then
    echo "nsys not found on PATH (install Nsight Systems)" >&2
    exit 1
fi

mkdir -p "${OUT_DIR}"

JARS=$(echo ${KFUSION_ROOT}/target/*.jar | tr ' ' ':')
JFLAGS="-Xms8G -Xmx8G -Dgraal.MaximumInliningSize=1000 -Dlog4j.configurationFile=${KFUSION_ROOT}/conf/log4j2.xml"
JFLAGS="${JFLAGS} -Dkfusion.nvtx=True -Dkfusion.max.frames=${FRAMES} ${KFUSION_JFLAGS}"

echo "settings : ${SETTINGS}"
echo "frames   : ${FRAMES}"
echo "report   : ${REPORT}.nsys-rep"

cd "${KFUSION_ROOT}"

# nsys must profile the JVM itself: the `tornado` launcher is a python script that execs java, and the
# CUDA injection does not follow that exec. Build the JVM command from the SDK argfile instead.
if [ ! -s "${TORNADOVM_HOME}/tornado-argfile" ]; then
    ${TORNADOVM_HOME}/bin/tornado --generate-argfile > /dev/null 2>&1 || true
fi

nsys profile \
    --output "${REPORT}" \
    --force-overwrite true \
    --trace=cuda,nvtx,osrt \
    --cuda-graph-trace=node \
    --sample=none \
    ${NSYS_EXTRA} \
    ${JAVA_HOME}/bin/java @${TORNADOVM_HOME}/tornado-argfile ${JFLAGS} \
        -cp "${JARS}" \
        kfusion.tornado.Benchmark --params="${SETTINGS}" \
    | tee "${REPORT}.log"

echo
echo "=== NVTX phase summary ==="
nsys stats --report nvtx_sum "${REPORT}.nsys-rep" 2>/dev/null | head -30 || true
echo
echo "=== CUDA kernel summary ==="
nsys stats --report cuda_gpu_kern_sum "${REPORT}.nsys-rep" 2>/dev/null | head -30 || true
echo
echo "=== CUDA memory ops ==="
nsys stats --report cuda_gpu_mem_time_sum "${REPORT}.nsys-rep" 2>/dev/null | head -20 || true
echo
echo "Open the timeline with: nsys-ui ${REPORT}.nsys-rep"
