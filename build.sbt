import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import Settings._
import Dependencies._
import AdvancedBuild._

inThisBuild(scalaVersion_213)
ThisBuild / organization := "de.rmgk"

val Libraries = new {

  val rescalaVersion = "0.30.0"
  val rescala        = libraryDependencies += "de.tuda.stg" %%% "rescala" % rescalaVersion


  val shared = Def.settings(
    strictCompile, scribe, scalatags, loci.communication, circe, rescala, loci.circe,
    loci.wsJavalin, scribeSlf4j
  )

  val main =
    Def.settings(strictCompile, betterFiles, decline,
                 scalatest, scalacheck, scalatestpluscheck,
                 jsoup, okHttp, javalin)


  val js: Def.SettingsDefinition = Seq(scalajsdom, normalizecss, fontawesome, scalatags,
                                       Resolvers.stg, strictCompile)
}

val vbundleDef = vbundle := {
  (app / Compile / fastOptJS).value
  val jsfile       = (app / Compile / fastOptJS / artifactPath).value
  val styles       = (app / Assets / SassKeys.sassify).value
  bundleStuff(jsfile, styles, target.value.toPath.resolve("resources/static"), fetchJSDependencies.value, sourceDirectory.value)
}

lazy val nativeImage = taskKey[File]("calls graalvm native image")

nativeImage := (viscel / GraalVMNativeImage / packageBin).value

lazy val viscel = project
                  .in(file("."))
                  .settings(
                    name := "viscel",
                    fork := true,
                    Libraries.main,
                    fetchJSDependenciesDef,
                    vbundleDef,
                    (Compile / compile) := ((Compile / compile) dependsOn vbundle).value,
                    publishLocal := publishLocal.dependsOn(sharedJVM / publishLocal).value,
                    selfversionDef,
                    (Compile / managedResources) += selfversion.value,
                    libraryDependencies += "com.nixxcode.jvmbrotli" % "jvmbrotli" % "0.2.0",
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
                 Libraries.js,
                 scalaJSUseMainModuleInitializer := true
               )
               .dependsOn(sharedJS)
               .enablePlugins(SbtSassify)

lazy val shared = crossProject(JSPlatform, JVMPlatform).in(file("shared"))
                  .settings(
                    name := "viscel-shared",
                    Libraries.shared,
                    )
lazy val sharedJVM = shared.jvm
lazy val sharedJS = shared.js

lazy val benchmarks = project.in(file("benchmarks"))
                      .settings(name := "benchmarks")
                      .enablePlugins(JmhPlugin)
                      .dependsOn(viscel)
