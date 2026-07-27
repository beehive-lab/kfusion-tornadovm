#!/bin/bash

echo "kfusion -Xmx8g -Xms1g -Dtornado.benchmarking=False kfusion.tornado.GUI "

JARS=$(echo ${KFUSION_ROOT}/target/*.jar | tr ' ' ':')

JFLAGS="-Xmx8g -Xms1g -Dtornado.kernels.coarsener=False -Dtornado.enable.fix.reads=False -Dtornado.compiler.fullInlining=True -Dlog4j.configurationFile=${KFUSION_ROOT}/conf/log4j2.xml -Dtornado.benchmarking=False -Dtornado.profiler=False -Dtornado.log.profiler=False"

CLASSPATH=${CLASSPATH}:${JARS} tornado --jvm="${JFLAGS}" kfusion.tornado.GUI 