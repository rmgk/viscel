import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._

name := "viscel"

version := "5.0.0-Beta"

scalaVersion := "2.10.4"

scalacOptions ++= (
  "-deprecation" ::
  "-encoding" :: "UTF-8" ::
  "-unchecked" ::
  "-feature" ::
  "-target:jvm-1.7" ::
  //"-language:implicitConversions" ::
  //"-language:reflectiveCalls" ::
  "-Xlint" ::
  "-Xfuture" ::
  //"-Xlog-implicits" ::
  Nil)



javaOptions ++= (
  "-verbose:gc" ::
  "-XX:+PrintGCDetails" ::
  "-Xverify:none" ::
  //"-server" ::
  //"-Xms16m" ::
  //"-Xmx1g" ::
	//"-Xss1m" ::
  //"-XX:MinHeapFreeRatio=5" ::
  //"-XX:MaxHeapFreeRatio=10" ::
  //"-XX:NewRatio=12" ::
  //"-XX:+UseSerialGC" ::
  //"-XX:+UseParallelGC" ::
  //"-XX:+UseParallelOldGC" ::
  //"-XX:+UseConcMarkSweepGC" ::
  //"-XX:+PrintTenuringDistribution" ::
  Nil)

defaultScalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(IndentWithTabs, true)
  .setPreference(CompactControlReadability, true)

resolvers ++= (
  ("Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/") ::
  ("Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/") ::
  // ("spray nightlies repo" at "http://nightlies.spray.io") ::
  ("spray repo" at "http://repo.spray.io") ::
  ("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/") ::
  Nil)

libraryDependencies ++= {
  val sprayVersion = "1.3.1"
  val neoVersion = "2.1.2"
  val akkaVersion = "2.3.3"
  // Database
  "org.neo4j" % "neo4j" % neoVersion :: // gpl3
  "org.neo4j" % "neo4j-graphviz" % neoVersion ::
  ("org.neo4j" % "neo4j-kernel" % neoVersion % "test" classifier "tests") ::
  "com.typesafe.slick" %% "slick" % "2.1.0-M2" :: // bsdish
  "org.xerial" % "sqlite-jdbc" % "3.7.2" :: // apache2
  // Webserver
  "io.spray" % "spray-caching" % sprayVersion :: // apache 2
  "io.spray" % "spray-can" % sprayVersion ::
  "io.spray" % "spray-client" % sprayVersion ::
  "io.spray" % "spray-http" % sprayVersion ::
  "io.spray" % "spray-httpx" % sprayVersion ::
  "io.spray" % "spray-routing" % sprayVersion ::
  "io.spray" % "spray-util" % sprayVersion ::
  // akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion ::
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion ::
  // "io.spray" %% "spray-json" % "1.2.6" ::
  // HTML
  // "com.netaporter" %% "scala-uri" % "0.4.2" :: // apache 2
  "org.jsoup" % "jsoup" % "1.7.3" :: // mit
  "com.scalatags" %% "scalatags" % "0.3.0" :: // mit
  // "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.5" :: // bsd
  // "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1" :: // apache2
  // Logging
  // "org.slf4j" % "slf4j-simple" % "1.7.7" :: // mit
  "ch.qos.logback" % "logback-classic" % "1.1.2" ::
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" :: // apache 2
  // Akka
  "com.typesafe.akka" %% "akka-actor" % "2.3.3" :: // apache 2
  // Commandline
  "jline" % "jline" % "2.12" ::
  "net.sf.jopt-simple" % "jopt-simple" % "4.6" :: // mit
  // "com.github.scopt" %% "scopt" % "3.1.0" :: // mit
  // "org.rogach" %% "scallop" % "0.9.4" :: //mit
  // Tests
  // "com.github.axel22" %% "scalameter" % "0.4" :: // new bsd
  "org.scalatest" %% "scalatest" % "2.2.0" % "test" ::
  // Misc
  // "com.chuusai" %% "shapeless" % "2.0.0" :: // apache 2
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3" :: // scala license (bsdish)
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3" ::
  // "com.twitter" %% "util-eval" % "6.5.0" :: // apache 2
  "commons-lang" % "commons-lang" % "2.6" :: // apache 2
  "org.scalactic" %% "scalactic" % "2.2.0" ::
  Nil
}


initialCommands in console := """
import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import akka.util.Timeout
//import com.twitter.util.Eval
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup._
import org.neo4j.graphdb._
import scala.collection.JavaConversions._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.can._
import spray.client.pipelining._
import spray.http._
import viscel._
import viscel.core._
import viscel.server._
import viscel.store._
"""
