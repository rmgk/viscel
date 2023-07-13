#!/usr/bin/sh

sbt --client stageJars
native-image --enable-preview --class-path "jvm/target/jars/*" viscel.Viscel
