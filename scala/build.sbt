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
	"-Xlint" ::
	"-Xfuture" ::
	//"-Xlog-implicits" ::
	"-Xfatal-warnings" ::
	"-Yno-adapted-args" ::
	//"-Ywarn-numeric-widen" ::
	//"-Ywarn-value-discard" ::
	"-Ywarn-dead-code" ::
	"-Yno-predef" ::
	//"-Yno-imports" ::
	Nil)

javaOptions ++= (
  "-verbose:gc" ::
  "-XX:+PrintGCDetails" ::
  //"-Xverify:none" ::
  //"-server" ::
  //"-Xms16m" ::
  //"-Xmx256m" ::
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

// gpl3
val neoDependencies = {
	val neoVersion = "2.1.5"
	"org.neo4j" % "neo4j" % neoVersion ::
	"org.neo4j" % "neo4j-graphviz" % neoVersion ::
	("org.neo4j" % "neo4j-kernel" % neoVersion % "test" classifier "tests") ::
	Nil
}

// apache 2
val sprayDependencies =
	List("spray-caching", "spray-can", "spray-client", "spray-http", "spray-httpx", "spray-routing", "spray-util")
		.map(n => "io.spray" %% n % "1.3.1")

val akkaDependencies =
	List("akka-actor", "akka-slf4j")
		.map(n => "com.typesafe.akka" %% n % "2.3.6")

// scala license (bsdish)
val ioDependencies =
	List("scala-io-core", "scala-io-file")
		.map(n => "com.github.scala-incubator.io" %% n % "0.4.3")

val otherDependencies =
  // HTML
  "org.jsoup" % "jsoup" % "1.8.1" :: // mit
  "com.scalatags" %% "scalatags" % "0.4.1" :: // mit
  // Logging
  "ch.qos.logback" % "logback-classic" % "1.1.2" ::
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" :: // apache 2
  // Commandline
  "jline" % "jline" % "2.12" ::
  "net.sf.jopt-simple" % "jopt-simple" % "4.7" :: // mit
  // Tests
  "org.scalatest" %% "scalatest" % "2.2.2" % "test" ::
	// Misc
  "org.scalactic" %% "scalactic" % "2.2.2" ::
	"org.scalaz" %% "scalaz-core" % "7.1.0" ::
  Nil

libraryDependencies ++= neoDependencies ++ sprayDependencies ++ akkaDependencies ++ ioDependencies ++ otherDependencies


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
