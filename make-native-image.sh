#!/usr/bin/sh

sbtn stageJars
native-image --class-path "jvm/target/jars/*" viscel.Viscel
