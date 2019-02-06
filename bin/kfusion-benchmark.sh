#!/bin/bash

if [ -z "${DATA_ROOT}" ]; then
    DATA_ROOT="${HOME}/data/kfusion/"
fi

if [ -z "${TRAJ_NUM}" ];then
    TRAJ_NUM="2"
fi


TRAJ_FLAGS="conf/bm-traj${TRAJ_NUM}.settings"
GROUND_TRUTH="${DATA_ROOT}/livingRoom${TRAJ_NUM}.gt.freiburg"
KFUSION_PROFILING_FLAGS="-Dtornado.profiling.enable=True -Dtornado.profiles.print=True -Dkfusion.kernels.print=True"

if [ -z "${KFUSION_FLAGS}" ];then
    KFUSION_FLAGS="-Xms8G"
fi

OUTPUT_PREFIX="bm-traj${TRAJ_NUM}"
HOSTNAME=$(hostname -s)
OUTPUT_SUFFIX="-${HOSTNAME}"

DATE=$(date '+%Y-%m-%d-%H:%M')

RESULTS_ROOT="${KFUSION_ROOT}/var/results/${DATE}"

if [ ! -e ${RESULTS_ROOT} ];then
    mkdir -p ${RESULTS_ROOT}
fi

RESULTS_CSV="${OUTPUT_PREFIX}-results${OUTPUT_SUFFIX}.csv"
printf "Writing results into %s/\n" ${RESULTS_ROOT} ${RESULTS_CSV}
echo "${KFUSION_FLAGS}" > ${RESULTS_ROOT}/kfusion.flags


echo "impl,ATE min,ATE max,ATE mean,ATE sd,ATE sum,acquisition min,acquisition max,acquisition mean,acquisition sd,acquisition sum,computation min,computation max,computation mean,computation sd,computation sum,integration min,integration max,integration mean,integration sd,integration sum,preprocessing min,preprocessing max,preprocessing mean,preprocessing sd,preprocessing sum,raycasting min,raycasting max,raycasting mean,raycasting sd,raycasting sum,rendering min,rendering max,rendering mean,rendering sd,rendering sum,total min,total max,total mean,total sd,total sum,tracking min,tracking max,tracking mean,tracking sd,tracking sum" > ${RESULTS_ROOT}/${RESULTS_CSV}

while [[ $# -gt 0 ]]
do
    TORNADO_CONFIG="$1"
    if [ -e "${TORNADO_CONFIG}" ]; then
        CONFIG_NAME=$(basename -s .conf ${TORNADO_CONFIG})
        printf "executing benchmark with config %s\n" ${CONFIG_NAME}
        TMP_FILE="${OUTPUT_PREFIX}-${CONFIG_NAME}${OUTPUT_SUFFIX}.tmp"
        ACTUAL_LOG_FILE="${OUTPUT_PREFIX}-${CONFIG_NAME}${OUTPUT_SUFFIX}-actual.log"
        PROFILING_LOG_FILE="${OUTPUT_PREFIX}-${CONFIG_NAME}${OUTPUT_SUFFIX}-profiled.log"
        KERNELS_FILE="${OUTPUT_PREFIX}-${CONFIG_NAME}${OUTPUT_SUFFIX}-kernels.csv"
        cp ${TORNADO_CONFIG} ${RESULTS_ROOT}/

        printf "executing timing run %s\n" ${TORNADO_CONFIG}
        ${KFUSION_ROOT}/bin/kfusion ${KFUSION_FLAGS} -Dtornado.config=${TORNADO_CONFIG} kfusion.tornado.Benchmark ${TRAJ_FLAGS} ${RESULTS_ROOT}/${ACTUAL_LOG_FILE}

        printf "executing profiling run %s\n" ${TORNADO_CONFIG}
        ${KFUSION_ROOT}/bin/kfusion ${KFUSION_FLAGS} ${KFUSION_PROFILING_FLAGS}  -Dtornado.opencl.eventwindow=81920 -Dtornado.config=${TORNADO_CONFIG} kfusion.tornado.Benchmark ${TRAJ_FLAGS} > ${RESULTS_ROOT}/${TMP_FILE}
        grep -v task "${RESULTS_ROOT}/${TMP_FILE}" > "${RESULTS_ROOT}/${PROFILING_LOG_FILE}"
        echo "device,task,time,submitted,start,end" > "${RESULTS_ROOT}/${KERNELS_FILE}"
        grep task "${RESULTS_ROOT}/${TMP_FILE}" >> "${RESULTS_ROOT}/${KERNELS_FILE}"
        perl -pi -e 's/task: //g' "${RESULTS_ROOT}/${KERNELS_FILE}"
        perl -pi -e 's/ /,/g' "${RESULTS_ROOT}/${KERNELS_FILE}"

        ${KFUSION_ROOT}/bin/emit-csv.py ${GROUND_TRUTH} ${RESULTS_ROOT}/${ACTUAL_LOG_FILE} "${HOSTNAME}-${CONFIG_NAME}-actual-${DATE}" >> ${RESULTS_ROOT}/${RESULTS_CSV}
        ${KFUSION_ROOT}/bin/emit-csv.py ${GROUND_TRUTH} ${RESULTS_ROOT}/${PROFILING_LOG_FILE} "${HOSTNAME}-${CONFIG_NAME}-profiled-${DATE}" >> ${RESULTS_ROOT}/${RESULTS_CSV}

        #rm "${RESULTS_ROOT}/${TMP_FILE}"
    fi
    shift # past argument or value
done


