# KFusion-TornadoVM #

A Java implementation of the Kinect Fusion application running on TornadoVM.

It can run on existing datasets as well as in real-time with input frames from an attached RBG-d camera.

The performance of the current implementation is outlined in the following [publication](https://www.researchgate.net/publication/327097904_Exploiting_High-Performance_Heterogeneous_Hardware_for_Java_Programs_using_Graal).

### Releases
  * KFusion-TornadoVM 0.1.0 - 11/02/2019: Initial release of Kinect Fusion on TornadoVM.
  
## How to start? ##

This implementation runs on TornadoVM to achieve GPU acceleration and real-time performance.
Hence, you need to install [Tornado](https://github.com/beehive-lab/Tornado) following the instructions from [Tornado-INSTALL](https://github.com/beehive-lab/Tornado/blob/master/INSTALL.md)

After you successfuly build Tornado, then issue the following commands to install locally its JAR files:

```bash
$ cd path/to/tornado
$ ./tornadoLocalInstallMaven
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

## Compile and run KFusion-TornadoVM
$ mvn clean install -DskipTests

## Run KFusion-TornadoVM GUI 
$ kfusion kfusion.tornado.GUI

## Run Benchmarking mode
$ kfusion kfusion.tornado.Benchmark <config file>
```

## Running KFusion-Tornado ##

KFusion can run in two modes receiving input from:

1) RGB-d camera where you select the input source from the drop-down menu:
```bash
## Run KFusion-Tornado GUI 
$ kfusion kfusion.tornado.GUI
```

2) Pre-defined datasets again through the GUI selection or:
```bash
## Run KFusion-Tornado GUI 
$ kfusion kfusion.tornado.Benchmark <config file>
```
In our examples, we use images from the [ICL-NUIM](https://www.doc.ic.ac.uk/~ahanda/VaFRIC/iclnuim.html) dataset which will be downloaded automatically when issuing the following command:

```bash
$ kfusion kfusion.java.Benchmark conf/bm-traj2.settings 
```

Note: 
* Sample configuration files are under the `conf/` directory.

## Output ##

If you enable the GUI while running KFusion you will see a real-time 3D space reconstruction similar to the image below:

![KFusion GUI output](doc/images/kfusion-gui-output.png)

In addition, you will see output text with performance metrics across the frames that KFusion processes:
```bash

	: Reading configuration file: /home/juanfumero/.kfusion_tornado/living_room_traj2_loop.raw
frame	acquisition	preprocessing	tracking	integration	raycasting	rendering	computation	total    	X          	Y          	Z         	tracked   	integrated
0	0.006214	0.003313	0.016029	0.027997	0.000000	0.001509	0.047339	0.055061	0.000000	0.000000	0.000000	0	1
1	0.000499	0.000389	0.002222	0.000608	0.000000	0.000000	0.003220	0.003719	0.000000	0.000000	0.000000	0	1
2	0.000409	0.000418	0.002108	0.000618	0.000000	0.000000	0.003144	0.003554	0.000000	0.000000	0.000000	0	1
3	0.000584	0.000431	0.003003	0.000593	0.000552	0.000000	0.004579	0.005163	0.000000	0.000000	0.000000	0	1
4	0.000665	0.000422	0.013228	0.000599	0.000369	0.000310	0.014618	0.015592	-0.004392	0.001433	0.000935	1	1
5	0.000669	0.000428	0.010570	0.000000	0.000328	0.000000	0.011327	0.011997	-0.002838	0.001069	0.000069	1	0
6	0.000503	0.000819	0.008678	0.000500	0.000282	0.000000	0.010279	0.010782	-0.003065	0.000643	0.000358	1	1
7	0.000390	0.000385	0.008401	0.000000	0.000282	0.000000	0.009068	0.009458	-0.004313	0.000814	0.000219	1	0
8	0.000393	0.000404	0.008534	0.000521	0.000278	0.000218	0.009737	0.010349	-0.006335	0.000110	0.000249	1	1
9	0.000408	0.000430	0.008646	0.000000	0.000282	0.000000	0.009358	0.009767	-0.006186	0.001268	0.000844	1	0
10	0.000405	0.000426	0.008429	0.000489	0.000275	0.000000	0.009619	0.010024	-0.006891	0.000413	0.001533	1	1
11	0.000401	0.000411	0.008062	0.000000	0.000280	0.000000	0.008754	0.009155	-0.006813	0.000033	0.000730	1	0
12	0.000390	0.000395	0.008920	0.000443	0.000251	0.000171	0.010009	0.010570	-0.010175	-0.000084	0.001281	1	1
13	0.000402	0.000416	0.007218	0.000001	0.000258	0.000000	0.007893	0.008295	-0.009811	0.000769	0.000688	1	0
14	0.000399	0.000420	0.007756	0.000482	0.000258	0.000000	0.008917	0.009316	-0.013333	0.002164	0.001499	1	1
15	0.000388	0.000382	0.007120	0.000000	0.000268	0.000000	0.007771	0.008159	-0.012245	0.000490	0.000430	1	0
16	0.000399	0.000401	0.009142	0.000456	0.000239	0.000173	0.010238	0.010810	-0.014947	-0.001196	-0.001099	1	1
17	0.000401	0.000404	0.006973	0.000000	0.000233	0.000000	0.007611	0.008012	-0.016376	-0.000913	-0.000052	1	0
18	0.000391	0.000386	0.006937	0.000417	0.000231	0.000000	0.007970	0.008362	-0.017928	-0.000768	-0.000490	1	1
19	0.000401	0.000408	0.007243	0.000001	0.000225	0.000000	0.007877	0.008278	-0.021001	-0.002088	-0.000866	1	0
20	0.000394	0.000382	0.010141	0.000429	0.000235	0.000179	0.011186	0.011759	-0.023981	-0.001167	-0.000530	1	1
21	0.000396	0.000399	0.010814	0.000000	0.000281	0.000000	0.011494	0.011890	-0.029775	-0.002021	-0.000998	1	0
22	0.000400	0.000435	0.010194	0.000408	0.000230	0.000000	0.011267	0.011668	-0.029229	-0.002887	-0.002649	1	1
23	0.000400	0.000379	0.006345	0.000000	0.000211	0.000000	0.006935	0.007335	-0.037086	-0.001378	-0.003249	1	0
24	0.000389	0.000371	0.008876	0.000400	0.000208	0.000146	0.009855	0.010390	-0.038548	0.000073	-0.004010	1	1
25	0.000395	0.000355	0.007435	0.000000	0.000225	0.000000	0.008015	0.008410	-0.044477	-0.001502	-0.003109	1	0
26	0.000438	0.000527	0.006604	0.000402	0.000211	0.000000	0.007743	0.008181	-0.049905	-0.002186	-0.005354	1	1
27	0.000388	0.000353	0.008214	0.000000	0.000217	0.000000	0.008783	0.009171	-0.054897	-0.000643	-0.005688	1	0
28	0.000385	0.000355	0.007154	0.000397	0.000220	0.000146	0.008126	0.008656	-0.060497	-0.000229	-0.008392	1	1
29	0.000386	0.000351	0.007138	0.000000	0.000223	0.000000	0.007711	0.008097	-0.069908	-0.000972	-0.006402	1	0
...
```

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

For academic collaborations please contact [Christos Kotselidis](https://www.kotselidis.net).


## Users Mailing list

A mailing list is also available to discuss Tornado related issues:

tornado-support@googlegroups.com

## Contributors 

This work was originated by James Clarkson and it is currently maintained by:

* [Juan Fumero](https://jjfumero.github.io/)
* [Michail Papadimitriou](https://mikepapadim.github.io)
* [Maria Xekalaki](https://github.com/mairooni)
* [Christos Kotselidis](https://www.kotselidis.net)

## License

The work is published under the Apache 2.0 license: [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
