#!/bin/bash
#optirun kfusion -Dtornado.ignore.intel=True --printKernel kfusion.tornado.Benchmark conf/bm-traj2.settings
optirun kfusion -Dtornado.ignore.intel=True kfusion.tornado.Benchmark conf/bm-traj2.settings
