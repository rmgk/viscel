import java.nio.file.Files

import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import Settings._
import Dependencies._

inThisBuild(scalaVersion_212)
inThisBuild(strictCompile)
ThisBuild / organization := "de.rmgk"


val Libraries = new {

  val rescalaVersion = "0.29.0"
  val rescala        = libraryDependencies += "de.tuda.stg" %%% "rescala" % rescalaVersion


  val shared = Def.settings(
    rmgkLogging, scalatags, loci.communication, circe, rescala, loci.circe, loci.wsAkka,
    scalaVersion_212,
    strictCompile
  )

  val main = Def.settings(betterFiles, decline, akkaHttp,
                          scalatest, scalacheck,
                          jsoup)


  val js: Def.SettingsDefinition = Seq(scalajsdom, normalizecss, fontawesome, scalatags,
                                       Resolvers.stg)
}

val vbundle = TaskKey[File]("vbundle", "bundles all the viscel resources")
val vbundleDef = vbundle := {
  (app / Compile / fastOptJS).value
  val jsfile = (app / Compile / fastOptJS / artifactPath).value
  val styles = (app / Assets / SassKeys.sassify).value
  val bundleTarget = target.value.toPath.resolve("resources/static")
  Files.createDirectories(bundleTarget)

  def gzipToTarget(f: File): Unit = IO.gzip(f, bundleTarget.resolve(f.name + ".gz").toFile)

  gzipToTarget(jsfile)
  val vidPath = sourceDirectory.value.toPath.resolve("main/vid").toFile
  val vids = IO.listFiles(vidPath)
  vids.foreach { f => IO.copyFile(f, bundleTarget.resolve(f.name).toFile) }
  gzipToTarget(jsfile.toPath.getParent.resolve(jsfile.name + ".map").toFile)
  styles.foreach(gzipToTarget)
  bundleTarget.toFile
}

lazy val viscel = project
                  .in(file("."))
                  .settings(
                    name := "viscel",
                    fork := true,
                    Libraries.main,
                    vbundleDef,
                    (Compile / compile) := ((Compile / compile) dependsOn vbundle).value,
                    publishLocal := publishLocal.dependsOn(sharedJVM / publishLocal).value
                  )
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

lazy val shared = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure).in(file("shared"))
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
