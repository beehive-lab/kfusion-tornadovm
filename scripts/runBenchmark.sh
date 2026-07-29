#!/bin/bash
#
# Runs the KFusion benchmark on a chosen TornadoVM backend and writes a clean trajectory table.
#
# usage: scripts/runBenchmark.sh <CUDA|OpenCL> [settings-file] [tag]
#   produces var/logs/<tag>.log (raw stdout) and var/logs/<tag>.table (header + per-frame rows)
#
# Environment:
#   TORNADOVM_HOME   TornadoVM SDK (must contain the requested backend)
#   KFUSION_ROOT     kfusion checkout (default: parent of this script)
#   KFUSION_JFLAGS   extra JVM flags, e.g. "-Dkfusion.cuda.graphs=False -Dkfusion.max.frames=50"

set -e

BACKEND=${1:-CUDA}
SETTINGS=${2:-conf/bm-traj2.settings}
TAG=${3:-$(basename ${SETTINGS} .settings)-$(echo ${BACKEND} | tr 'A-Z' 'a-z')}

KFUSION_ROOT=${KFUSION_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}
LOG=${KFUSION_ROOT}/var/logs/${TAG}.log
TABLE=${KFUSION_ROOT}/var/logs/${TAG}.table

if [ -z "${TORNADOVM_HOME}" ]; then
    echo "TORNADOVM_HOME is not set" >&2
    exit 1
fi

mkdir -p "$(dirname ${LOG})"

JARS=$(echo ${KFUSION_ROOT}/target/*.jar | tr ' ' ':')
# jdk.graal.MaximumInliningSize would be ignored: the frozen Graal in TornadoVM reads the graal.* prefix.
# The sketcher's limit is 2x this value and the legacy map-reduce path needs slightly more than 600 nodes.
JFLAGS="-Xms8G -Xmx8G -Dgraal.MaximumInliningSize=1000"
JFLAGS="${JFLAGS} -Dkfusion.tornado.backend=${BACKEND}"
JFLAGS="${JFLAGS} -Dlog4j.configurationFile=${KFUSION_ROOT}/conf/log4j2.xml ${KFUSION_JFLAGS}"

echo "backend  : ${BACKEND}"
echo "settings : ${SETTINGS}"
echo "log      : ${LOG}"

cd "${KFUSION_ROOT}"
CLASSPATH=${CLASSPATH}:${JARS} ${TORNADOVM_HOME}/bin/tornado --jvm="${JFLAGS}" \
    kfusion.tornado.Benchmark --params="${SETTINGS}" 2>&1 | tee "${LOG}"

# the trajectory table starts at the header line; checkPos.py and compareRuns.py want just that
sed -n '/^frame\t/,$p' "${LOG}" | grep -E '^(frame|[0-9])' > "${TABLE}" || true
echo "table    : ${TABLE} ($(wc -l < ${TABLE}) lines)"

GT=${GROUND_TRUTH:-${HOME}/data/kfusion/livingRoom2.gt.freiburg}
if [ -s "${GT}" ] && [ -s "${TABLE}" ]; then
    echo
    python3 bin/checkPos.py "${TABLE}" "${GT}" | tail -12 || true
fi
