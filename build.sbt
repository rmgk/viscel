import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import Settings._
import Dependencies._
import AdvancedBuild._

def  lociRef(name: String) = ProjectRef(uri("git://github.com/scala-loci/scala-loci.git#77944c620bbeff3e97ea8aa40d8af6b8838ec422"), name)

lazy val lociJavalinJVM = lociRef("lociCommunicatorWsJavalinJVM")
lazy val lociJavalinJS = lociRef("lociCommunicatorWsJavalinJS")


inThisBuild(scalaVersion_213)
ThisBuild / organization := "de.rmgk"

ThisBuild / Compile / doc / sources:= Seq.empty
ThisBuild / Compile / packageDoc / publishArtifact := false

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
                    jsoup, okHttp, javalin,
                    fetchJSDependenciesDef,
                    vbundleDef,
                    jsoniter,
                    (Compile / compile) := ((Compile / compile) dependsOn vbundle).value,
                    publishLocal := publishLocal.dependsOn(sharedJVM / publishLocal).value,
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
                      "-H:+JNI",
                      "-H:+RemoveSaturatedTypeFlows"
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
                 Resolvers.stg, strictCompile,
                 scalaJSUseMainModuleInitializer := true,
                 jsoniter
               )
               .dependsOn(sharedJS)
               .enablePlugins(SbtSassify)

lazy val shared = crossProject(JSPlatform, JVMPlatform).in(file("shared"))
                  .settings(
                    name := "viscel-shared",
                    strictCompile, scribe, scalatags,
                    scribeSlf4j, Resolvers.stg,
                    libraryDependencies += "de.tuda.stg" %%% "rescala" % "0.30.0",
                    //libraryDependencies += "io.lemonlabs" %%% "scala-uri" % "2.2.2",
                    jsoniter,
                    Compile / sourceGenerators += Def.task {
                      val file = (Compile / sourceManaged).value / "viscel" / "shared" / "Version.scala"
                      val outstring = s"""package viscel.shared; object Version { val str = "${version.value}"}"""
                      val current = try IO.read(file) catch {case _: Throwable => ""}
                      if (current != outstring) IO.write(file, outstring)
                      Seq(file)
                    }
                    )
lazy val sharedJVM = shared.jvm
                           .dependsOn(lociJavalinJVM)
lazy val sharedJS = shared.js
                          .dependsOn(lociJavalinJS)

lazy val benchmarks = project.in(file("benchmarks"))
                      .settings(name := "benchmarks")
                      .enablePlugins(JmhPlugin)
                      .dependsOn(viscel)


