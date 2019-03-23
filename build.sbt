import java.nio.file.Files

import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import Settings._
import Dependencies._

val commonSettings: sbt.Def.SettingsDefinition = Seq(
    scalaVersion := version_212,
    Compile / compile / scalacOptions ++= strictScalacOptions,
)

val Libraries = new {

  val rescalaVersion = "0.24.0"
  val rescalatags = libraryDependencies += "de.tuda.stg" %%% "rescalatags" % rescalaVersion
  val rescala = libraryDependencies += "de.tuda.stg" %%% "rescala" % rescalaVersion


  val shared = Def.settings(
    rmgkLogging, scalatags, loci.communication, circe, rescala, loci.circe, loci.wsAkka
  )

  val main = Def.settings(scalactic, jsoup, betterFiles, decline, akkaHttp,
                                    scalatest, scalacheck)


  val js: Def.SettingsDefinition = Seq(scalajsdom, normalizecss, fontawesome, rescalatags,
                                       Resolvers.stg)
}

val vbundle = TaskKey[File]("vbundle", "bundles all the viscel resources")
val vbundleDef = vbundle := {
  val jsfile = (web / Compile / fastOptJS / artifactPath).value
  val styles = (web / Assets / SassKeys.sassify).value
  val bundleTarget = (Universal / target).value.toPath.resolve("stage/static")
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
      commonSettings,
      fork := true,
      Libraries.main,
      vbundleDef,
      (Compile / compile) := ((Compile / compile) dependsOn vbundle).value,
      )
    .enablePlugins(JavaServerAppPackaging)
    .dependsOn(sharedJVM)

lazy val web = project.in(file("web"))
               .enablePlugins(ScalaJSPlugin)
               .settings(
                 name := "web",
                 commonSettings,
                 Libraries.js,
                 scalaJSUseMainModuleInitializer := true,
                 )
               .dependsOn(sharedJS)
               .enablePlugins(SbtSassify)

lazy val shared = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure).in(file("shared"))
                  .settings(
                    name := "shared",
                    commonSettings,
                    Libraries.shared,
                    )
lazy val sharedJVM = shared.jvm
lazy val sharedJS = shared.js

lazy val benchmarks = project.in(file("benchmarks"))
                      .settings(name := "benchmarks",
                                commonSettings)
                      .enablePlugins(JmhPlugin)
                      .dependsOn(viscel)
