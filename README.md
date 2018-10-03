# README #

Tornado implementation of Kfusion.

## Quickstart ##

```
#!bash
$ cd slambench-tornado/

## Exports 
$ export KFUSION_ROOT="${PWD}"
$ export PATH="${PATH}:${KFUSION_ROOT}/bin"
$ export JAVA_HOME=/path/to/graal/jdk1.8.0_131
$ export GRAAL_ROOT=/path/to/graal/graal-core/mxbuild/dists
$ export TORNADO_ROOT=/path/to/tornado
$ export PATH="${PATH}:${KFUSION_ROOT}/bin:${TORNADO_ROOT}/bin/bin/"
$ export TORNADO_SDK=${TORNADO_ROOT}/bin/sdk
$ export GRAAL_VERSION=0.22
$ export JVMCI_VERSION=1.8.0_131

## Get the slambench-java
./getDependencies.sh 


## Compile and run slambench-tornado
$ mvn clean install -DskipTests
$ kfusion kfusion.tornado.GUI
$ kfusion kfusion.tornado.Benchmark <config file>
```

Note: 
* sample configuration files from SLAMBench are under the `conf/` directory.
