/* This file is shared between multiple projects
 * and may contain unused dependencies */

import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*
import sbt.*
import sbt.Keys.libraryDependencies

object Dependencies {

  def directories = libraryDependencies += "dev.dirs"        % "directories" % "26"
  val jol         = libraryDependencies += "org.openjdk.jol" % "jol-core"    % "0.17"
  val jsoniterScala =
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.23.2"
  val jsoup         = libraryDependencies += "org.jsoup"                    % "jsoup"            % "1.16.1"
  val munit         = libraryDependencies += "org.scalameta"              %%% "munit"            % "1.0.0-M8" % Test
  val munitCheck    = libraryDependencies += "org.scalameta"              %%% "munit-scalacheck" % "1.0.0-M8" % Test
  val okHttp        = libraryDependencies += "com.squareup.okhttp3"         % "okhttp"           % "4.10.0"
  val pprint        = libraryDependencies += "com.lihaoyi"                %%% "pprint"           % "0.8.0"
  val quicklens     = libraryDependencies += "com.softwaremill.quicklens" %%% "quicklens"        % "1.9.0"
  val scalacheck    = libraryDependencies += "org.scalacheck"             %%% "scalacheck"       % "1.17.0"   % Test
  val scalaJavaTime = libraryDependencies += "io.github.cquiroz"          %%% "scala-java-time"  % "2.3.0"
  val scalajsDom    = libraryDependencies += "org.scala-js"               %%% "scalajs-dom"      % "2.6.0"
  val scalatags     = libraryDependencies += "com.lihaoyi"                %%% "scalatags"        % "0.12.0"
  val scribe        = libraryDependencies += "com.outr"                   %%% "scribe"           % "3.10.7"
  val scribeSlf4j   = libraryDependencies += "com.outr"                    %% "scribe-slf4j"     % "3.10.7"
  val scribeSlf4j2  = libraryDependencies += "com.outr"                    %% "scribe-slf4j2"    % "3.10.7"
  val sourcecode    = libraryDependencies += "com.lihaoyi"                %%% "sourcecode"       % "0.3.0"
  val sqliteJdbc    = libraryDependencies += "org.xerial"                   % "sqlite-jdbc"      % "3.42.0.0"
  val upickle       = libraryDependencies += "com.lihaoyi"                %%% "upickle"          % "3.1.2"

  object slips {
    val chain   = libraryDependencies += "de.rmgk.slips" %%% "chain"   % "0.5.0"
    val delay   = libraryDependencies += "de.rmgk.slips" %%% "delay"   % "0.5.0"
    val logging = libraryDependencies += "de.rmgk.slips" %%% "logging" % "0.5.0"
    val options = libraryDependencies += "de.rmgk.slips" %%% "options" % "0.7.0"
    val scip    = libraryDependencies += "de.rmgk.slips" %%% "scip"    % "0.5.0"
    val script  = libraryDependencies += "de.rmgk.slips" %%% "script"  % "0.7.0"
  }

  object loci {
    def generic(n: String) =
      // use jitpack or no?
      if (false)
        libraryDependencies += "io.github.scala-loci" %%% s"scala-loci-$n" % "0.5.0"
      else
        libraryDependencies += "com.github.scala-loci.scala-loci" %%% s"scala-loci-$n" % "eb0719f08f"

    val communication = generic("communication")
    val circe         = generic("serializer-circe")
    val tcp           = generic("communicator-tcp")
    val upickle       = generic("serializer-upickle")
    val jsoniterScala = generic("serializer-jsoniter-scala")
    val webrtc        = generic("communicator-webrtc")
    val wsAkka        = generic("communicator-ws-akka")
    val wsWeb         = generic("communicator-ws-webnative")
    val wsJavalin     = generic("communicator-ws-javalin")
    val wsJetty       = generic("communicator-ws-jetty")
    val wsJetty11     = generic("communicator-ws-jetty11")
  }
}
