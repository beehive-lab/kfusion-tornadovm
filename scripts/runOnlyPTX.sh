#!/bin/bash

echo "kfusion  kfusion.tornado.Benchmark conf/bm-traj2.settings "

JARS=$(echo ${KFUSION_ROOT}/target/*.jar | tr ' ' ':')

JFLAGS="-Xms4G -Xmx4G -Dtornado.kernels.coarsener=False -Dtornado.enable.fix.reads=False -Dlog4j.configurationFile=${KFUSION_ROOT}/conf/log4j2.xml -Dtornado.benchmarking=False -Dtornado.profiler=False -Dtornado.log.profiler=False -Dtornado.ptx.priority=100"

CLASSPATH=${CLASSPATH}:${JARS} tornado --jvm="${JFLAGS}" kfusion.tornado.Benchmark --params="conf/bm-traj2.settings" 

#kfusion -Xms4g -Xmx4g -Dtornado.benchmarking=False -Dtornado.profiler=False -Dtornado.log.profiler=False kfusion.tornado.Benchmark conf/bm-traj2.settings 

