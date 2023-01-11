#!/usr/bin/sh

rm -r jvm/src/main/resources/META-INF/native-image/generated/

sbtn stageJars

cs java --jvm graalvm-java17:22.3.0 -agentlib:native-image-agent=config-output-dir=jvm/src/main/resources/META-INF/native-image/generated --class-path "jvm/target/jars/*" viscel.Viscel --static target/resources/static/ --basedir target/base



