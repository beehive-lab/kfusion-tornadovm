# README #

Tornado implementation of Kfusion.

## Quickstart ##

```
#!bash
$ . ${TORNADO_ROOT}/etc/tornado.env
$ cd slambench-tornado/
$ export KFUSION_ROOT="${PWD}"
$ export PATH="${PATH}:${KFUSION_ROOT}/bin"
$ mvn clean install -DskipTests
$ kfusion kfusion.java.GUI
$ kfusion kfusion.java.Benchmark <config file>
```

Note: 
* sample configuration files from SLAMBench are under conf/
* you will need sample data from SLAMBench for it to work