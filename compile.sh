#!/bin/bash
export KFUSION_ROOT="${PWD}"
export PATH="${PATH}:${KFUSION_ROOT}/bin"
mvn clean install -DskipTests

