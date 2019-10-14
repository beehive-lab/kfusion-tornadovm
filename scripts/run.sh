#!/bin/bash

echo "kfusion  kfusion.tornado.Benchmark conf/bm-traj2.settings "

kfusion -Xms512m -Xmx512m -Dtornado.benchmarking=False -Dtornado.profiler=True -Dtornado.log.profiler=True kfusion.tornado.Benchmark conf/bm-traj2.settings 

