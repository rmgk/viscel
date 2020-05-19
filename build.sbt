import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import Settings._
import Dependencies._
import AdvancedBuild._

def  lociRef(name: String) = ProjectRef(uri("git://github.com/scala-loci/scala-loci.git#02ad8351e3248d4c9b054eb8ff159daf1a6ca773"), name)

lazy val lociJavalinJVM = lociRef("lociCommunicatorWsJavalinJVM")
lazy val lociJavalinJS = lociRef("lociCommunicatorWsJavalinJS")
lazy val lociSerializerUpickleJS = lociRef("lociSerializerUpickleJS")
lazy val lociSerializerUpickleJVM = lociRef("lociSerializerUpickleJVM")


inThisBuild(scalaVersion_213)
ThisBuild / organization := "de.rmgk"

val vbundleDef = vbundle := {
  (app / Compile / fullOptJS).value
  val jsfile       = (app / Compile / fullOptJS / artifactPath).value
  val styles       = (app / Assets / SassKeys.sassify).value
  bundleStuff(jsfile, styles, target.value.toPath.resolve("resources/static"), fetchJSDependencies.value, sourceDirectory.value, version.value)
}

lazy val nativeImage = taskKey[File]("calls graalvm native image")

nativeImage := (viscel / GraalVMNativeImage / packageBin).value

lazy val viscel = project
                  .in(file("."))
                  .settings(
                    name := "viscel",
                    fork := true,
                    strictCompile, betterFiles, decline,
                    scalatest, scalacheck, scalatestpluscheck,
                    jsoup, okHttp, javalin, circe,
                    fetchJSDependenciesDef,
                    vbundleDef,
                    (Compile / compile) := ((Compile / compile) dependsOn vbundle).value,
                    publishLocal := publishLocal.dependsOn(sharedJVM / publishLocal).value,
                    selfversionDef,
                    (Compile / managedResources) += selfversion.value,
                    //  experimental graalvm options
                    // javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
                    graalVMNativeImageOptions ++= List(
                      "--allow-incomplete-classpath",
                      "--no-fallback",
                      //"--report-unsupported-elements-at-runtime",
                      "--initialize-at-build-time",
                      // "-H:+ReportExceptionStackTraces",
                      "-H:EnableURLProtocols=http,https",
                      // "--enable-all-security-services",
                      "-H:+JNI"
                    )
                  )
                  .enablePlugins(GraalVMNativeImagePlugin)
                  .enablePlugins(JavaServerAppPackaging)
                  .dependsOn(sharedJVM)




lazy val app = project.in(file("app"))
               .enablePlugins(ScalaJSPlugin)
               .settings(
                 name := "app",
                 scalajsdom, normalizecss, scalatags,
                 Resolvers.stg, strictCompile
                 //scalaJSUseMainModuleInitializer := true
               )
                      .dependsOn(sharedJS)

               .enablePlugins(SbtSassify)

lazy val shared = crossProject(JSPlatform, JVMPlatform).in(file("shared"))
                  .settings(
                    name := "viscel-shared",
                    strictCompile, scribe, scalatags, upickle,
                    scribeSlf4j, Resolvers.stg,
                    libraryDependencies += "de.tuda.stg" %%% "rescala" % "0.30.0"
                    )
lazy val sharedJVM = shared.jvm
                           .dependsOn(lociJavalinJVM)
                           .dependsOn(lociSerializerUpickleJVM)
lazy val sharedJS = shared.js
                          .dependsOn(lociSerializerUpickleJS)
                          .dependsOn(lociJavalinJS)

lazy val benchmarks = project.in(file("benchmarks"))
                      .settings(name := "benchmarks")
                      .enablePlugins(JmhPlugin)
                      .dependsOn(viscel)


