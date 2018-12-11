#!/bin/bash

echo "kfusion  kfusion.tornado.Benchmark conf/bm-traj2.settings "

kfusion -XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics -Dtornado.benchmarking=False kfusion.tornado.Benchmark conf/bm-traj2.settings 

