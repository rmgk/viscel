#!/bin/sh

mkdir dependencies
cd dependencies
mkdir rescala
cd rescala
git init
git remote add origin git@github.com:rescala-lang/REScala.git
git fetch origin c3c37c4739b4a0b0ab40adc5c94473d0f8ccc498
git reset --hard c3c37c4739b4a0b0ab40adc5c94473d0f8ccc498
sbt publishLocal
cd ..
mkdir loci
git init
git remote add origin git@github.com:scala-loci/scala-loci.git
git fetch origin a880ec8639ff48f6d85a869cb8a3117dfa06287a
git reset --hard a880ec8639ff48f6d85a869cb8a3117dfa06287a
sbt publishLocal
cd ..
cd ..
