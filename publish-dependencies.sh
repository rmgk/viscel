#!/bin/sh

mkdir dependencies
cd dependencies
git clone https://github.com/rescala-lang/REScala.git rescala
cd rescala
git reset --hard e7d59bbf2280caa25305aa8b4db9c66ed5a60f51
sbt publishLocal
cd ..


git clone https://github.com/scala-loci/scala-loci.git loci
cd loci
git reset --hard 4294a4797dde181ebfa8a82203299c50b9f8fc9b
sbt publishLocal
cd ..
cd ..
