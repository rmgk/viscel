import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._

name := "ScalaViscel"

version := "5.0.0-β"

scalaVersion := "2.10.3"

scalaSource in Compile <<= baseDirectory {(base) => new File(base, "src")}

scalaSource in Test <<= baseDirectory {(base) => new File(base, "test")}

resourceDirectory in Compile <<= baseDirectory {(base) => new File(base, "resources")}

scalacOptions ++= Seq(
	"-deprecation",
	"-encoding", "UTF-8",
	"-unchecked",
	"-feature",
	"-target:jvm-1.7",
	"-Xlint"
)

javaOptions ++= Seq(
	"-verbose:gc",
	"-XX:+PrintGCDetails",
	"-Xverify:none",
	//"-server",
	//"-Xms16m",
	//"-Xmx1g",
	//"-XX:MinHeapFreeRatio=5",
	//"-XX:MaxHeapFreeRatio=10",
	//"-XX:NewRatio=12",
	//"-XX:+UseSerialGC",
	//"-XX:+UseParallelGC",
	//"-XX:+UseParallelOldGC",
	//"-XX:+UseConcMarkSweepGC",
	//"-XX:+PrintTenuringDistribution",
	""
)

scalariformSettings

ScalariformKeys.preferences := FormattingPreferences()
	.setPreference(IndentWithTabs, true)
	.setPreference(CompactControlReadability, true)

resolvers ++= Seq(
	"Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
	"Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
	"spray nightlies repo" at "http://nightlies.spray.io",
	"spray repo" at "http://repo.spray.io",
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= {
val sprayVersion = "1.2-20131011"
val neoVersion = "2.0.0-M06"
Seq(
	// Database
	"org.neo4j" % "neo4j" % neoVersion, // gpl3
	"org.neo4j" % "neo4j-graphviz" % neoVersion,
	"org.neo4j" % "neo4j-kernel" % neoVersion % "test" classifier "tests",
	"org.slf4j" % "slf4j-simple" % "1.7.5", // mit
	"com.typesafe.slick" %% "slick" % "1.0.1", // bsdish
	"org.xerial" % "sqlite-jdbc" % "3.7.2", // apache2
	// Webserver
	"io.spray" % "spray-caching" % sprayVersion, // apache 2
	"io.spray" % "spray-can" % sprayVersion,
	"io.spray" % "spray-client" % sprayVersion,
	"io.spray" % "spray-http" % sprayVersion,
	"io.spray" % "spray-httpx" % sprayVersion,
	"io.spray" % "spray-routing" % sprayVersion,
	"io.spray" % "spray-util" % sprayVersion,
	"io.spray" %% "spray-json" % "1.2.5",
	// HTML
	"com.github.theon" %% "scala-uri" % "0.4.0-SNAPSHOT", // apache 2
	"org.jsoup" % "jsoup" % "1.7.2", // mit
	"com.scalatags" %% "scalatags" % "0.1.4", // mit (according to pom)
	// "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.5", // bsd
	// "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1", // apache2
	// Commandline
	"org.scala-lang" % "jline" % "2.10.3",
	"net.sf.jopt-simple" % "jopt-simple" % "4.5", // mit
	// "com.github.scopt" %% "scopt" % "3.1.0", // mit
	// "org.rogach" %% "scallop" % "0.9.4", //mit
	// Tests
	"com.github.axel22" %% "scalameter" % "0.3", // new bsd
	"org.scalatest" %% "scalatest" % "1.9.1" % "test",
	// Misc
	"com.chuusai" %% "shapeless" % "1.2.4", // apache 2
	"com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2", // scala license (bsdish)
	"com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
	"com.typesafe" %% "scalalogging-slf4j" % "1.0.1", // apache 2
	// "com.twitter" %% "util-eval" % "6.5.0", // apache 2
	"com.typesafe.akka" %% "akka-actor" % "2.2.1", // apache 2
	"commons-lang" % "commons-lang" % "2.6" // apache 2
)
}


initialCommands in console := """
import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import akka.util.Timeout
import com.twitter.util.Eval
import com.typesafe.scalalogging.slf4j.Logging
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
