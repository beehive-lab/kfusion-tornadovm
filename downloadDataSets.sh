#!/bin/bash
#
# Downloads an ICL-NUIM trajectory and converts it to the flat KFusion .raw format.
#
# Unlike the original script this does NOT need slambench: the conversion is done by
# kfusion.tools.Scene2Raw, which streams the tarball directly (nothing is extracted to disk).
#
# usage: ./downloadDataSets.sh [tgz-url] [output-raw-name]
#   defaults: http://www.doc.ic.ac.uk/~ahanda/living_room_traj2_loop.tgz  living_room_traj2_loop.raw
#
# The tarball and the ground-truth trajectory are kept in ${DATA_ROOT} (default ~/data/kfusion),
# the converted .raw lands in ~/.kfusion_tornado/ where RawDevice looks for it.

set -e

url=${1:-http://www.doc.ic.ac.uk/~ahanda/living_room_traj2_loop.tgz}
file=${2:-living_room_traj2_loop.raw}

DATA_ROOT=${DATA_ROOT:-${HOME}/data/kfusion}
RAW_ROOT=${HOME}/.kfusion_tornado
KFUSION_ROOT=${KFUSION_ROOT:-$(cd "$(dirname "$0")" && pwd)}

tarball=$(basename "${url}")
# living_room_traj2_loop.tgz -> 2 -> livingRoom2.gt.freiburg
traj=$(echo "${tarball}" | sed -n 's/.*traj\([0-9]\+\).*/\1/p')
groundTruth="livingRoom${traj:-2}.gt.freiburg"
groundTruthUrl="https://www.doc.ic.ac.uk/~ahanda/VaFRIC/${groundTruth}"

mkdir -p "${DATA_ROOT}" "${RAW_ROOT}"

echo "Dataset root : ${DATA_ROOT}"
echo "Tarball      : ${url}"
echo "Ground truth : ${groundTruthUrl}"
echo "Output       : ${RAW_ROOT}/${file}"

if [ ! -s "${DATA_ROOT}/${tarball}" ]; then
    echo "Downloading ${tarball} (~2GB, resumable) ..."
fi
wget -c -O "${DATA_ROOT}/${tarball}" "${url}"

if [ ! -s "${DATA_ROOT}/${groundTruth}" ]; then
    wget -c -O "${DATA_ROOT}/${groundTruth}" "${groundTruthUrl}" || \
        echo "WARNING: could not fetch ${groundTruth} - ATE checking will not be available"
fi

if [ ! -d "${KFUSION_ROOT}/target/classes" ]; then
    echo "KFusion is not built yet: run ./compile.sh first" >&2
    exit 1
fi

echo "Converting to raw ..."
java --enable-preview -cp "${KFUSION_ROOT}/target/classes:${KFUSION_ROOT}/target/*" \
    kfusion.tools.Scene2Raw "${DATA_ROOT}/${tarball}" "${RAW_ROOT}/${file}"

echo "done"
