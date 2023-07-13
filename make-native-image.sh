#!/usr/bin/sh

sbt --client stageJars
native-image --enable-preview -march=x86-64-v2 --class-path "jvm/target/jars/*" viscel.Viscel
