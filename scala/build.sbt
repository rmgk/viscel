name := "ScalaViscel"

scalaVersion := "2.10.1"

scalaSource in Compile <<= baseDirectory {(base) => new File(base, "src")}

resolvers ++= Seq(
	"Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
	"Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
	"spray nightlies repo" at "http://nightlies.spray.io",
	"spray repo" at "http://repo.spray.io",
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
	"com.chuusai" %% "shapeless" % "1.2.4",
	"com.typesafe.slick" %% "slick" % "1.0.0",
	"org.xerial" % "sqlite-jdbc" % "3.7.2",
	"org.slf4j" % "slf4j-simple" % "1.7.5",
	"io.spray" % "spray-can" % "1.2-20130516",
	"io.spray" % "spray-client" % "1.2-20130516",
	"io.spray" % "spray-util" % "1.2-20130516",
	"io.spray" % "spray-routing" % "1.2-20130516",
	"io.spray" % "spray-httpx" % "1.2-20130516",
	"io.spray" % "spray-caching" % "1.2-20130516",
	"io.spray" % "spray-http" % "1.2-20130516",
	"com.typesafe.akka" %% "akka-actor" % "2.2-M3",
	"io.spray" %% "spray-json" % "1.2.4",
	"org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
	"com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2",
	"com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
	"commons-lang" % "commons-lang" % "2.6"
)

seq(ProguardPlugin.proguardSettings :_*)

proguardOptions += keepMain("Server")

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)
