#!/bin/bash
#
# Runs the KFusion benchmark on the TornadoVM CUDA backend.
#
# usage: scripts/runCUDA.sh [settings-file] [output-log]
#   defaults: conf/bm-traj2.settings   var/logs/bm-traj2-<timestamp>.log
#
# Environment:
#   KFUSION_ROOT     kfusion checkout (default: parent of this script)
#   TORNADOVM_HOME   TornadoVM SDK built with 'make BACKEND=cuda'
#   KFUSION_JFLAGS   extra JVM flags (e.g. "-Dkfusion.cuda.graphs=False")

set -e

KFUSION_ROOT=${KFUSION_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}
SETTINGS=${1:-conf/bm-traj2.settings}
STAMP=$(date '+%Y-%m-%d-%H%M%S')
LOG=${2:-${KFUSION_ROOT}/var/logs/$(basename ${SETTINGS} .settings)-${STAMP}.log}

if [ -z "${TORNADOVM_HOME}" ]; then
    echo "TORNADOVM_HOME is not set (point it at the CUDA SDK build)" >&2
    exit 1
fi

mkdir -p "$(dirname ${LOG})"

JARS=$(echo ${KFUSION_ROOT}/target/*.jar | tr ' ' ':')
# jdk.graal.MaximumInliningSize: the sketcher's inlining limit is 2x this value, and
# IterativeClosestPoint.reduceValues (the legacy map-reduce path) sits just above the default 600.
JFLAGS="-Xms8G -Xmx8G -Djdk.graal.MaximumInliningSize=1000 -Dlog4j.configurationFile=${KFUSION_ROOT}/conf/log4j2.xml ${KFUSION_JFLAGS}"

echo "settings : ${SETTINGS}"
echo "log      : ${LOG}"
echo "jvm      : ${JFLAGS}"

cd "${KFUSION_ROOT}"
CLASSPATH=${CLASSPATH}:${JARS} ${TORNADOVM_HOME}/bin/tornado --jvm="${JFLAGS}" \
    kfusion.tornado.Benchmark --params="${SETTINGS}" | tee "${LOG}"

GT=${GROUND_TRUTH:-${HOME}/data/kfusion/livingRoom2.gt.freiburg}
if [ -s "${GT}" ]; then
    echo
    echo "Trajectory accuracy against ${GT}:"
    # the benchmark table starts after the banner lines, checkPos.py wants it to start at the header
    sed -n '/^frame\t/,$p' "${LOG}" > "${LOG}.table"
    python3 bin/checkPos.py "${LOG}.table" "${GT}" || true
else
    echo "ground truth ${GT} not found - run ./downloadDataSets.sh to fetch it"
fi
