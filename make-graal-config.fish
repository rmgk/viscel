#!/usr/bin/fish

rm -r jvm/src/main/resources/META-INF/native-image/generated/

set JAVA_HOME /home/ragnar/Sync/Apps/GraalVM


sbt --client "reload; stageJars; vbundle"

$JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=jvm/src/main/resources/META-INF/native-image/generated --class-path "jvm/target/jars/*" viscel.Viscel --static target/resources/static/ --basedir target/base
