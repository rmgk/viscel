import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._

name := "ScalaViscel"

version := "5.0.0-β"

scalaVersion := "2.10.2"

scalaSource in Compile <<= baseDirectory {(base) => new File(base, "src")}

scalaSource in Test <<= baseDirectory {(base) => new File(base, "test")}

scalacOptions ++= List(
	"-deprecation",
	"-encoding", "UTF-8",
	"-unchecked",
	"-feature",
	"-target:jvm-1.7",
	"-Xlint"
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

libraryDependencies ++= Seq(
	"com.chuusai" %% "shapeless" % "1.2.4", // apache 2
	"com.github.axel22" %% "scalameter" % "0.3", // new bsd
	"com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2", // scala license (bsdish)
	"com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
	"com.github.scopt" %% "scopt" % "3.1.0", // mit
	"com.scalatags" %% "scalatags" % "0.1.4", // mit (according to pom)
	"com.typesafe" %% "scalalogging-slf4j" % "1.0.1", // apache 2
	"com.typesafe.akka" %% "akka-actor" % "2.2.0", // apache 2
	"com.typesafe.slick" %% "slick" % "1.0.1", // bsdish
	"commons-lang" % "commons-lang" % "2.6", // apache 2
	"io.spray" % "spray-caching" % "1.2-20130801", // apache 2
	"io.spray" % "spray-can" % "1.2-20130801",
	"io.spray" % "spray-client" % "1.2-20130801",
	"io.spray" % "spray-http" % "1.2-20130801",
	"io.spray" % "spray-httpx" % "1.2-20130801",
	"io.spray" % "spray-routing" % "1.2-20130801",
	"io.spray" % "spray-util" % "1.2-20130801",
	"io.spray" %% "spray-json" % "1.2.5",
	"org.jsoup" % "jsoup" % "1.7.2", // mit
	"org.neo4j" % "neo4j" % "2.0.0-M04", // gpl3
	"org.neo4j" % "neo4j-graphviz" % "2.0.0-M04",
	"org.rogach" %% "scallop" % "0.9.4", //mit
	"org.slf4j" % "slf4j-simple" % "1.7.5", //mit
	"org.xerial" % "sqlite-jdbc" % "3.7.2", // apache2
	// "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.5", //bsd
	// "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1", // apache2
	"org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
	"org.neo4j" % "neo4j-kernel" % "2.0.0-M04" % "test" classifier "tests",
	"net.sf.jopt-simple" % "jopt-simple" % "4.5",
	"org.scala-lang" % "jline" % "2.10.2"
)


initialCommands in console := """
import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import akka.util.Timeout
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
import viscel.store._
def getPipe() = {
	implicit val system = ActorSystem()
	implicit val timeout: Timeout = 30.seconds
	(system, sendReceive(IO(Http)))
}
"""
