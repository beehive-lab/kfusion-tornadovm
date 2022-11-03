#!/bin/bash

echo "kfusion -Xmx512m -Xms512m -Dtornado.benchmarking=False kfusion.tornado.GUI "

JARS=$(echo ${KFUSION_ROOT}/target/*.jar | tr ' ' ':')

JFLAGS="-Xmx512m -Xms512m -Dtornado.kernels.coarsener=False -Dtornado.enable.fix.reads=False -Dlog4j.configurationFile=${KFUSION_ROOT}/conf/log4j2.xml -Dtornado.benchmarking=False -Dtornado.profiler=False -Dtornado.log.profiler=False"

CLASSPATH=${CLASSPATH}:${JARS} tornado --jvm="${JFLAGS}" kfusion.tornado.GUI 