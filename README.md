# README #

Tornado implementation of Kfusion.

## Quickstart ##

```bash
# Setup:
export KFUSION_ROOT="${PWD}"
export PATH="${PATH}:${KFUSION_ROOT}/bin"
export JAVA_HOME=/path/to/graal/jdk1.8.0_131
export TORNADO_ROOT=/path/to/tornado
export PATH="${PATH}:${TORNADO_ROOT}/bin/bin/"
export TORNADO_SDK=${TORNADO_ROOT}/bin/sdk

## Compile and run kfusion-tornado
$ mvn clean install -DskipTests

## Run KFusion-Tornado GUI 
$ kfusion kfusion.tornado.GUI

## Run Benchmarking mode
$ kfusion kfusion.tornado.Benchmark <config file>
```

Example:
```bash
$ kfusion kfusion.java.Benchmark conf/bm-traj2.settings 
```


Note: 
* sample configuration files from SLAMBench are under the `conf/` directory.
