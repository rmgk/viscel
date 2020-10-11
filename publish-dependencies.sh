#!/bin/sh

mkdir dependencies
cd dependencies
git clone https://github.com/rescala-lang/REScala.git rescala
cd rescala
git reset --hard c3c37c4739b4a0b0ab40adc5c94473d0f8ccc498
sbt publishLocal
cd ..
git clone https://github.com/scala-loci/scala-loci.git loci
cd loci
git reset --hard a880ec8639ff48f6d85a869cb8a3117dfa06287a
sbt publishLocal
cd ..
cd ..
