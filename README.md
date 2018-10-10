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

## Get the slambench-java
./getDependencies.sh 


## Compile and run slambench-tornado
$ mvn clean install -DskipTestsi
$ kfusion kfusion.tornado.GUI
$ kfusion kfusion.tornado.Benchmark <config file>
```

Note: 
* sample configuration files from SLAMBench are under the `conf/` directory.
