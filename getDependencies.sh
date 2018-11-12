#!/bin/sh 

## Get Slambench-java
current=`pwd`
cd /tmp
git clone git@github.com:beehive-lab/slambench-java.git  
cd slambench-java 
git checkout feature/misc/juan
mvn clean install -DskipTests -Djava8 
cp target/slambench-java-0.0.2.jar $current/lib
cd ..
rm -Rf /tmp/slambench-java 
