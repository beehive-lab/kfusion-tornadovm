# KFusion-TornadoVM #

A Java implementation of the Kinect Fusion application running on TornadoVM.
It can run on existing datasets as well as realtime with an RBG-d camera.

### Releases
  * KFusion-TornadoVM 0.1.0 - 11/02/2019: Initial release of Kinect Fusion on TornadoVM.
  
## How to start? ##

This implementation runs on TornadoVM to achieve GPU acceleration and real-time performance.
Hence, you need to install [Tornado](https://github.com/beehive-lab/Tornado) following the instructions from [Tornado-INSTALL](https://github.com/beehive-lab/Tornado/blob/master/INSTALL.md)

After you successfuly build Tornado, then issue the following commands to install locally its JAR files:

```bash
$ cd path/to/tornado
$ tornadoLocalInstallMaven
```

Finally, you can install KFusion-TornadoVM by issuing the following commands:


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

## Running KFusion-Tornado ##

KFusion can run in two modes receiving input from:

1) RGB-d camera where you select the input source from the drop-down menu.
```bash
## Run KFusion-Tornado GUI 
$ kfusion kfusion.tornado.GUI
```

2) Pre-defined datasets
```bash
## Run KFusion-Tornado GUI 
$ kfusion kfusion.tornado.Benchmark <config file>
```
In our examples, we use datasets from the ICL-NUIM which will be downloaded automatically when issuing the following command:
```bash
$ kfusion kfusion.java.Benchmark conf/bm-traj2.settings 
```

Note: 
* Sample configuration files from SLAMBench are under the `conf/` directory.

## Output ##

If you enable the GUI while running KFusion you will see a real-time 3D space reconstruction similar to the image below:


In addition, you will see output text with performance metrics across the frames that KFusion processes:



## Selected Publications

* James Clarkson, Juan Fumero, Michalis Papadimitriou, Foivos S. Zakkak, Maria Xekalaki, Christos Kotselidis, Mikel Luján (The University of Manchester). **Exploiting High-Performance Heterogeneous Hardware for Java Programs using Graal**. *Proceedings of the 15th International Conference on Managed Languages & Runtime.* [preprint](https://www.researchgate.net/publication/327097904_Exploiting_High-Performance_Heterogeneous_Hardware_for_Java_Programs_using_Graal)

* Sajad Saeedi, Bruno Bodin, Harry Wagstaff, Andy Nisbet, Luigi Nardi, John Mawer, Nicolas Melot, Oscar Palomar, Emanuele Vespa, Tom Spink, Cosmin Gorgovan, Andrew Webb, James Clarkson, Erik Tomusk, Thomas Debrunner, Kuba Kaszyk, Pablo Gonzalez-de-Aledo, Andrey Rodchenko, Graham Riley, Christos Kotselidis, Björn Franke, Michael FP O'Boyle, Andrew J Davison, Paul HJ Kelly, Mikel Luján, Steve Furber. **Navigating the Landscape for Real-Time Localization and Mapping for Robotics and Virtual and Augmented Reality.** In Proceedings of the IEEE, 2018.

* C. Kotselidis, J. Clarkson, A. Rodchenko, A. Nisbet, J. Mawer, and M. Luján. **Heterogeneous Managed Runtime Systems: A Computer Vision Case Study.** In Proceedings of the 13th ACM SIGPLAN/SIGOPS International Conference on Virtual Execution Environments, VEE ’17, [link](https://dl.acm.org/citation.cfm?doid=3050748.3050764)

### Citation

Please use the following citation if you use Tornado in your work.

```bibtex
@inproceedings{Clarkson:2018:EHH:3237009.3237016,
 author = {Clarkson, James and Fumero, Juan and Papadimitriou, Michail and Zakkak, Foivos S. and Xekalaki, Maria and Kotselidis, Christos and Luj\'{a}n, Mikel},
 title = {{Exploiting High-performance Heterogeneous Hardware for Java Programs Using Graal}},
 booktitle = {Proceedings of the 15th International Conference on Managed Languages \& Runtimes},
 series = {ManLang '18},
 year = {2018},
 isbn = {978-1-4503-6424-9},
 location = {Linz, Austria},
 pages = {4:1--4:13},
 articleno = {4},
 numpages = {13},
 url = {http://doi.acm.org/10.1145/3237009.3237016},
 doi = {10.1145/3237009.3237016},
 acmid = {3237016},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {Java, graal, heterogeneous hardware, openCL, virtual machine},
} 
```

## Acknowledgments

This work was initially supported by the EPSRC grants [PAMELA EP/K008730/1](http://apt.cs.manchester.ac.uk/projects/PAMELA/) and [AnyScale Apps EP/L000725/1](http://anyscale.org), and now it is funded by the [EU Horizon 2020 E2Data 780245](https://e2data.eu) and the [EU Horizon 2020 ACTiCLOUD 732366](https://acticloud.eu) grants.

## Collaborations

We welcome collaborations! Please see how to contribute in the [CONTRIBUTIONS](CONTRIBUTIONS.md).

For academic collaborations please contact [Christos Kotselidis](https://www.kotselidis.net).


## Users Mailing list

A mailing list is also available to discuss Tornado related issues:

tornado-support@googlegroups.com

## Contributors 

This work was originated by James Clarkson under the joint supervision of [Mikel Luján](https://www.linkedin.com/in/mikellujan/) and [Christos Kotselidis](https://www.kotselidis.net). 
Currently, this project is maintained and updated by the following contributors:

* [Juan Fumero](https://jjfumero.github.io/)
* [Michail Papadimitriou](https://mikepapadim.github.io)
* [Maria Xekalaki](https://github.com/mairooni)
* [Christos Kotselidis](https://www.kotselidis.net)

## License

The work is published under the Apache 2.0 license: [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
