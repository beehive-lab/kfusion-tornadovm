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
: "${KFUSION_ROOT:?KFUSION_ROOT is not set. Run 'source source.env' from the repo root first.}"

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

# Java's Swing UI doesn't pick up Linux HiDPI/fractional scaling on its own
# (it assumes a plain 96 DPI screen unless told otherwise), which makes every
# panel and font render tiny on a HiDPI display. Derive a scale factor from
# Xft.dpi (what the rest of the desktop is already scaled to) and let
# KFUSION_UI_SCALE override it for setups where that heuristic guesses wrong.
# macOS already scales Swing/AWT correctly for Retina displays on its own
# (sun.java2d.uiScale is a Windows/Linux-only JEP 263 property), so this is
# skipped there to leave that platform's launch untouched.
UI_SCALE_ARGS=()
if [ "$(uname -s)" = "Linux" ]; then
    if [ -n "${KFUSION_UI_SCALE}" ]; then
        UI_SCALE="${KFUSION_UI_SCALE}"
    else
        XFT_DPI=$(xrdb -query 2>/dev/null | awk -F: '/^Xft\.dpi/ {gsub(/[ \t]/, "", $2); print $2}')
        UI_SCALE=$(awk -v dpi="${XFT_DPI:-96}" 'BEGIN { s = dpi / 96; if (s < 1) s = 1; printf "%.2f", s }')
    fi
    UI_SCALE_ARGS=("-Dsun.java2d.uiScale=${UI_SCALE}")
fi

echo "kfusion (GUI) -Xmx20g -Xms2g ${UI_SCALE_ARGS[*]} kfusion.tornado.GUI"

java @"${ARGFILE}" \
    -Xmx20g -Xms2g \
    "${UI_SCALE_ARGS[@]}" \
    --add-opens java.desktop/sun.awt=ALL-UNNAMED \
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
