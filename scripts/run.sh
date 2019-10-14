#!/bin/bash

echo "kfusion  kfusion.tornado.Benchmark conf/bm-traj2.settings "

kfusion -Xms512m -Xmx512m -Dtornado.benchmarking=False -Dtornado.profiler=False -Dtornado.log.profiler=False kfusion.tornado.Benchmark conf/bm-traj2.settings 

