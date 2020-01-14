# Usage with Spark

In other to use ibis in Spark, Spark needs to be build with some jars from ibis
and when running a Spark application some jars from ibis also needs to be available in the classpath.

For instructions on how to build Spark for use with ibis serialization see the read me here: [Modified version of Spark](https://github.com/dadepo/spark/tree/v2.4.4-with-ibis)

Find below the instructions on how to build the jars that needs to be on the classpath.

These jars are:
 - ibis-spark-io-2.3.3.jar
 - ibis-spark-util-2.3.3.jar
 
To generate these jars, follow the following procedure:
 
 - make sure to have maven install
 - from the root of the project run 
 - `./gradlew install`
 - The jars would be generated and can be copied from `~/.m2/repository/ipl-spark/`

See [Modified version of Spark](https://github.com/dadepo/spark/tree/v2.4.4-with-ibis) for more instructions on how to use ibis serialization with spark and how to run the spark example applications 

# Ibis Portability Layer

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.1324956.svg)](https://doi.org/10.5281/zenodo.1324956)

Ibis is an open source Java grid software project of the Computer
Systems group of the Computer Science department of the Faculty of
Sciences at the Vrije Universiteit, Amsterdam, The Netherlands.  The
main goal of the Ibis project is to create an efficient Java-based
platform for grid computing.

This release contains the Ibis communication library (defined
by the Ibis Portability Layer (IPL)) and several implementations of this
IPL. Some example applications are provided in the "examples"
directory.

Building the IPL is easy: on Unix, execute the included "gradlew" script,
on Windows execute "gradlew.bat".

The users's guide in the docs directory ("docs/usersguide.pdf") explains
how to compile and run your Ibis application.

The programmer's manual ("docs/progman.pdf") contains a detailed
description of the Ibis Application Programmer's interface (API),
illustrated with example code fragments.

The javadoc of Ibis is available in "javadoc/index.html".

Ibis has its own web-site: http://www.cs.vu.nl/ibis/.  There, you can
find more Ibis documentation, papers, application sources.

The current Ibis  source repository tree is accessible at GitHub:
"https://github.com/junglecomputing/ipl".

There is some dispute about the pronounciation of the word "Ibis". The
file "docs/rob.mp3" shows how one of the Ibis designers feels about this
issue.

## Legal

The IPL library is copyrighted by the Vrije Universiteit Amsterdam and released
under the Apache License, Version 2.0. A copy of the license may be obtained
from [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0).

IPL uses several third-party libraries. Details are below.

This product includes software developed by the Apache Software
Foundation (http://www.apache.org/).

The BCEL copyright notice lives in "notices/LICENSE.bcel.txt".  The
Log4J copyright notice lives in "notices/LICENSE.log4j.txt".  The
SLF4J copyright notice lives in "notices/LICENSE.slf4j.txt".  The
Commons copyright notice lives in "notices/LICENSE.apache-2.0.txt".
The ASM copyright notice lives in "notices/LICENSE.asm.txt".

This product includes jstun, which is distributed with a dual license,
one of which is version 2.0 of the Apache license. It lives in
"notices/LICENSE.apache-2.0.txt".

This product includes the UPNP library from SuperBonBon Industries. Its
license lives in "notices/LICENSE.apache-2.0.txt".

This product includes the trilead SSH-2 library. Its license
lives in "notices/LICENSE.trilead.txt".

This product includes software developed by TouchGraph LLC
(http://www.touchgraph.com/). Its license lives in
"notices/LICENSE.TG.txt".
