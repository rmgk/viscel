#!/usr/bin/sh

sbtn stageJars
native-image --enable-preview --class-path "jvm/target/jars/*" viscel.Viscel
