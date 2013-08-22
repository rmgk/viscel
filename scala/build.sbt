import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._

name := "ScalaViscel"

version := "5.0.0-α"

scalaVersion := "2.10.2"

scalaSource in Compile <<= baseDirectory {(base) => new File(base, "src")}

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

resolvers ++= Seq(
	"Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
	"Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
	"spray nightlies repo" at "http://nightlies.spray.io",
	"spray repo" at "http://repo.spray.io",
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
	"com.chuusai" %% "shapeless" % "1.2.4",
	"com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2",
	"com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
	"com.scalatags" %% "scalatags" % "0.1.4",
	"com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
	"com.typesafe.akka" %% "akka-actor" % "2.2.0",
	"com.typesafe.slick" %% "slick" % "1.0.1",
	"commons-lang" % "commons-lang" % "2.6",
	"io.spray" % "spray-caching" % "1.2-20130801",
	"io.spray" % "spray-can" % "1.2-20130801",
	"io.spray" % "spray-client" % "1.2-20130801",
	"io.spray" % "spray-http" % "1.2-20130801",
	"io.spray" % "spray-httpx" % "1.2-20130801",
	"io.spray" % "spray-routing" % "1.2-20130801",
	"io.spray" % "spray-util" % "1.2-20130801",
	"io.spray" %% "spray-json" % "1.2.5",
	"net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.5",
	"org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
	"org.jsoup" % "jsoup" % "1.7.2",
	"org.neo4j" % "neo4j" % "2.0.0-M04",
	"org.neo4j" % "neo4j-graphviz" % "2.0.0-M04",
	"org.slf4j" % "slf4j-simple" % "1.7.5",
	"org.xerial" % "sqlite-jdbc" % "3.7.2"
)
