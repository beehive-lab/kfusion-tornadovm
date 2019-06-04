#!/bin/bash

echo "kfusion  kfusion.tornado.Benchmark conf/bm-traj2.settings "

kfusion -Dtornado.benchmarking=False kfusion.tornado.Benchmark conf/bm-traj2.settings 

