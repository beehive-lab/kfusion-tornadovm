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
-Dtornado.precompiled.binary=fpgaKernels/mm2meters/lookupBufferAddress,pp.mm2meters.device=0:1,fpgaKernels/mm2meters/lookupBufferAddress,pp.bilateralFilter.device=0:1 \                                 
-Dpp.mm2meters.device=0:1 \                                                                                                                                                                              
-Dpp.bilateralFilter.device=0:1 \                                                                                                                                                                              
-Dtornado.opencl.codecache.loadbin=True \                                                                                                                                                                      
-Dpp.bilateralFilter.global.dims=320,240 \                                                                                                                                                                     
-Dpp.bilateralFilter.local.dims=64,16 \                                                                                                                                                                        
-Dpp.mm2meters.global.dims=320,240 \                                                                                                                                                                     
-Dpp.mm2meters.local.dims=64,16 \                                                                                                                                                                        
kfusion.tornado.Benchmark conf/bm-traj2.settings  

elif [[ $1 == "raycast" ]] 
then

kfusion \
-Dtornado.opencl.timer.kernel=False \
-Draycast.raycast.device=0:1 \
-Dtornado.opencl.codecache.loadbin=True \
-Dtornado.precompiled.binary=fpgaKernels/lookupBufferAddress,raycast.raycast.device=0:1 \
-Draycast.raycast.global.dims=320,240 \
-Draycast.raycast.local.dims=4,4 \
kfusion.tornado.Benchmark conf/bm-traj2.settings 

else 
        echo "Provide kernel to run on the FGPA: $ runFPGA.sh integrate|renderTrack|mm2meters " 
fi

