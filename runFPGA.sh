#!/bin/bash

if [[ $1 == "integrate" ]]
then

kfusion \
-Dtornado.opencl.timer.kernel=False \
-Dintegrate.integrate.device=0:1 \
-Dtornado.opencl.codecache.loadbin=True \
-Dtornado.precompiled.binary=fpgaKernels/fpgaIntegrate/lookupBufferAddress,integrate.integrate.device=0:1 \
-Dintegrate.integrate.global.dims=256,256 \
-Dintegrate.integrate.local.dims=4,4 \
kfusion.tornado.Benchmark conf/bm-traj2.settings 

elif [[ $1 == "renderTrack" ]] 
then

kfusion \
-Dtornado.opencl.timer.kernel=False \
-DrenderTrack.renderTrack.device=0:1 \
-Dtornado.opencl.codecache.loadbin=True \
-Dtornado.precompiled.binary=fpgaKernels/lookupBufferAddress,renderTrack.renderTrack.device=0:1 \
-DrenderTrack.renderTrack.global.dims=320,240 \
-DrenderTrack.renderTrack.local.dims=64,16 \
kfusion.tornado.Benchmark conf/bm-traj2.settings 

elif [[ $1 == "mm2meters" ]] 
then

kfusion \
-Dtornado.opencl.timer.kernel=False \
-Dmm2metersKernel.mm2metersKernel.device=0:1 \
-DbilateralFilter.bilateralFilter.device=0:1 \
-Dtornado.opencl.codecache.loadbin=True \
-Dtornado.precompiled.binary=fpgaKernels/mm2meters/lookupBufferAddress,mm2metersKernel.mm2metersKernel.device=0:1,fpgaKernels/mm2meters/lookupBufferAddress,bilateralFilter.bilateralFilter.device=0:1 \
-DbilateralFilter.bilateralFilter.global.dims=320,240 \
-DbilateralFilter.bilateralFilter.local.dims=64,16 \
-Dmm2metersKernel.mm2metersKernel.global.dims=320,240 \
-Dmm2metersKernel.mm2metersKernel.local.dims=64,16 \
kfusion.tornado.Benchmark conf/bm-traj2.settings 

else 
        echo "Provide kernel to run on the FGPA: $ runFPGA.sh integrate|renderTrack|mm2meters " 
fi

