#!/bin/bash

echo "kfusion  kfusion.tornado.Benchmark conf/bm-traj2.settings "

kfusion -Xms4g -Xmx4g -Dtornado.benchmarking=False -Dtornado.profiler=False -Dtornado.log.profiler=False kfusion.tornado.Benchmark conf/bm-traj2.settings 

