#!/bin/bash
#optirun kfusion -Dtornado.ignore.platform=Intel --printKernel kfusion.tornado.Benchmark conf/bm-traj2.settings
optirun kfusion -Dtornado.ignore.platform=Intel kfusion.tornado.Benchmark conf/bm-traj2.settings
