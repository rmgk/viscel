import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import Settings._
import Dependencies._

val commonSettings: sbt.Def.SettingsDefinition = Seq(
    scalaVersion := version_212,
    Compile / compile / scalacOptions ++= strictScalacOptions)

val Libraries = new {

  val rescalaVersion = "0.24.0"
  val rescalatags = libraryDependencies += "de.tuda.stg" %%% "rescalatags" % rescalaVersion
  val rescala = libraryDependencies += "de.tuda.stg" %%% "rescala" % rescalaVersion

  val shared = Def.settings(
    resolvers ++= Resolvers.all,
    rmgkLogging, scalatags, lociCommunication, circe, rescala, lociCommunicationCirce
  )

  val main = Def.settings(scalactic, jsoup, betterFiles, decline, akkaHttp, akkaStream,
                                    scalatest, scalacheck)



  val js: Def.SettingsDefinition = Seq(scalajsdom, normalizecss, fontawesome, rescalatags)
}

lazy val viscel = project.in(file("."))
                  .settings(
                    name := "viscel",
                    commonSettings,
                    fork := true,
                    Libraries.main,
                    (Compile / compile) := ((Compile / compile) dependsOn (web / Compile / fastOptJS)).value,
                    Compile / compile := ((compile in Compile) dependsOn (web / Assets / SassKeys.sassify)).value,
                    (Compile / resources) += (web / Compile / fastOptJS / artifactPath).value,
                    (Compile / resources) ++= (web / Assets / SassKeys.sassify).value,
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
