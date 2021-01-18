import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._
import sbt.Keys._

object Dependencies {

  // define versions, The variable name must be camel case by module name
  // format: off
  object Versions {
    val akkaActors = "2.6.10"
    val akkaHttp = "10.2.2"
    val betterFiles = "3.9.1"
    val catsCore = "2.3.1"
    val circeCore = "0.13.0"
    val decline = "1.3.0"
    val fastparse = "2.3.0"
    val javalin = "3.12.0"
    val jline = "2.14.6"
    val jodaConvert = "2.2.1"
    val jodaTime = "2.10.3"
    val jsoniterScalaCore = "2.6.2"
    val jsoup = "1.13.1"
    val jsr166y = "1.7.0"
    val kaleidoscope = "0.1.0"
    val magnolia = "0.15.0"
    val normalizecss = "8.0.1"
    val okHttp = "4.9.0"
    val pprint = "0.6.0"
    val reactiveStreams = "1.0.3"
    val retypecheck = "0.8.0"
    val scala211 = "2.11.11"
    val scala212 = "2.12.4"
    val scala213 = "2.13.4"
    val scala300 = "3.0.0-M3"
    val scalaJavaTime = "2.1.0"
    val scalaLociCommunication = "0.4.0"
    val scalaParallelCollections = "1.0.0"
    val scalaSwing = "3.0.0"
    val scalaXml = "1.3.0"
    val scalacheck = "1.15.2"
    val scalactic = "3.0.0"
    val scalajsDom = "1.1.0"
    val scalatags = "0.9.2"
    val scalatest = "3.2.3"
    val scalatestpluscheck = "3.2.2.0"
    val scribe = "3.2.1"
    val sourcecode = "0.2.1"
    val tomlScala = "0.2.2"
    val upickle = "1.2.2"
  }
  // format: on

  import Dependencies.{Versions => V}

  val betterFiles     = Def.setting("com.github.pathikrit" %% "better-files" % V.betterFiles)
  val catsCore        = Def.setting("org.typelevel" %%% "cats-core" % V.catsCore)
  val decline         = Def.setting("com.monovore" %%% "decline" % V.decline)
  val fastparse       = Def.setting("com.lihaoyi" %%% "fastparse" % V.fastparse)
  val javalin         = Def.setting("io.javalin" % "javalin" % V.javalin)
  val jline           = Def.setting("jline" % "jline" % V.jline)
  val jodaConvert     = Def.setting("org.joda" % "joda-convert" % V.jodaConvert)
  val jodaTime        = Def.setting("joda-time" % "joda-time" % V.jodaTime)
  val jsoup           = Def.setting("org.jsoup" % "jsoup" % V.jsoup)
  val jsr166y         = Def.setting("org.codehaus.jsr166-mirror" % "jsr166y" % V.jsr166y)
  val kaleidoscope    = Def.setting("com.propensive" %%% "kaleidoscope" % V.kaleidoscope)
  val magnolia        = Def.setting("com.propensive" %%% "magnolia" % V.magnolia)
  val normalizecss    = Def.setting("org.webjars.npm" % "normalize.css" % V.normalizecss)
  val okHttp          = Def.setting("com.squareup.okhttp3" % "okhttp" % V.okHttp)
  val pprint          = Def.setting("com.lihaoyi" %%% "pprint" % V.pprint)
  val reactiveStreams = Def.setting("org.reactivestreams" % "reactive-streams" % V.reactiveStreams)
  val retypecheck     = Def.setting("de.tuda.stg" %% "retypecheck" % V.retypecheck)
  val scalacheck      = Def.setting("org.scalacheck" %%% "scalacheck" % V.scalacheck % "test")
  val scalactic       = Def.setting("org.scalactic" %% "scalactic" % V.scalactic)
  val scalaJavaTime   = Def.setting("io.github.cquiroz" %%% "scala-java-time" % V.scalaJavaTime)
  val scalajsDom      = Def.setting("org.scala-js" %%% "scalajs-dom" % V.scalajsDom)
  val scalaParallelCollections =
    Def.setting("org.scala-lang.modules" %% "scala-parallel-collections" % V.scalaParallelCollections)
  val scalaSwing         = Def.setting("org.scala-lang.modules" %% "scala-swing" % V.scalaSwing)
  val scalatags          = Def.setting("com.lihaoyi" %%% "scalatags" % V.scalatags)
  val scalatest          = Def.setting("org.scalatest" %%% "scalatest" % V.scalatest % "test")
  val scalatestpluscheck = Def.setting("org.scalatestplus" %%% "scalacheck-1-14" % V.scalatestpluscheck % "test")
  val scalaXml           = Def.setting("org.scala-lang.modules" %% "scala-xml" % V.scalaXml)
  val scribe             = Def.setting("com.outr" %%% "scribe" % V.scribe)
  val scribeSlf4j        = Def.setting("com.outr" %% "scribe-slf4j" % V.scribe)
  val sourcecode         = Def.setting("com.lihaoyi" %%% "sourcecode" % V.sourcecode)
  val tomlScala          = Def.setting("tech.sparse" %%% "toml-scala" % V.tomlScala)
  val upickle            = Def.setting("com.lihaoyi" %% "upickle" % V.upickle)

  val jsoniterScalaAll = Def.setting(Seq(
    ("com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core"   % V.jsoniterScalaCore exclude ("io.github.cquiroz", s"scala-java-time-tzdb_sjs1_${scalaVersion.value.substring(0, 4)}")),
    "com.github.plokhotnyuk.jsoniter-scala"   %% "jsoniter-scala-macros" % V.jsoniterScalaCore % "provided"
  ))

  val akkaHttpAll = Def.setting(Seq("akka-http-core", "akka-http")
    .map(n => "com.typesafe.akka" %% n % V.akkaHttp) ++
    Seq("com.typesafe.akka" %% "akka-stream" % V.akkaActors))

  val circeAll = Def.setting(Seq("core", "generic", "generic-extras", "parser")
    .map(n => "io.circe" %%% s"circe-$n" % V.circeCore))

  object loci {
    def generic(n: String) = Def.setting("de.tuda.stg" %%% s"scala-loci-$n" % V.scalaLociCommunication)

    val communication = generic("communication")

    val circe     = generic("serializer-circe")
    val tcp       = generic("communicator-tcp")
    val upickle   = generic("serializer-upickle")
    val webrtc    = generic("communicator-webrtc")
    val wsAkka    = generic("communicator-ws-akka")
    val wsJavalin = generic("communicator-ws-javalin")
  }

}
