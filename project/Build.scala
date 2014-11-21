import sbt._
import sbt.Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._

object Build extends sbt.Build {

	lazy val viscel = project.in(file("."))
		.settings(name := "viscel")
		.settings(Settings.main: _*)
		.settings(Libraries.main: _*)
		.settings(compile in Compile <<= (compile in Compile) dependsOn (fullOptJS in(js, Compile)))
		.settings(resources in Compile += artifactPath.in(js, Compile, fullOptJS).value)


	lazy val js = project.in(file("js"))
		.settings(name := "viscel-js")
		.settings(scalaJSSettings: _*)
		.settings(Settings.common: _*)
		.settings(Libraries.js: _*)


}

object Settings {
	lazy val common = List(

		version := "5.0.0-Beta",
		scalaVersion := "2.10.4",

		scalacOptions ++= (
			"-deprecation" ::
			"-encoding" :: "UTF-8" ::
			"-unchecked" ::
			"-feature" ::
			"-target:jvm-1.7" ::
			"-Xlint" ::
			"-Xfuture" ::
			//"-Xlog-implicits" ::
			"-Yno-predef" ::
			//"-Yno-imports" ::
			"-Ywarn-all" ::
			"-Xfatal-warnings" ::
			"-Yinline-warnings" ::
			"-Yno-adapted-args" ::
			"-Ywarn-dead-code" ::
			"-Ywarn-nullary-override" ::
			"-Ywarn-nullary-unit" ::
			//"-Ywarn-numeric-widen" ::
			//"-Ywarn-value-discard" ::
			Nil),

		resolvers ++= (
			("Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/") ::
			("Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/") ::
			// ("spray nightlies repo" at "http://nightlies.spray.io") ::
			("spray repo" at "http://repo.spray.io") ::
			("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/") ::
			Nil))


	lazy val main: List[Def.Setting[_]] = common ++ List(

		fork := true,

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
			Nil),

		initialCommands in console :=
			"""import akka.actor.{ ActorSystem, Props, Actor }
				|import akka.io.IO
				|import akka.util.Timeout
				|//import com.twitter.util.Eval
				|import com.typesafe.scalalogging.slf4j.StrictLogging
				|import org.jsoup._
				|import org.neo4j.graphdb._
				|import scala.collection.JavaConversions._
				|import scala.concurrent._
				|import scala.concurrent.duration._
				|import scala.concurrent.ExecutionContext.Implicits.global
				|import spray.can._
				|import spray.client.pipelining._
				|import spray.http._
				|import viscel._
				|import viscel.crawler._
				|import viscel.server._
				|import viscel.store._
				|import viscel.database._
				|import scala.Predef._
			""".stripMargin)

}

object Libraries {

	lazy val main: List[Def.Setting[_]] = List(libraryDependencies ++= neo ++ spray ++ akka ++ logging ++
		commandline ++ scalatest ++ scalactic ++ jsoup ++ scalatags.value ++ rescala ++ argonaut)

	lazy val js: List[Def.Setting[_]] = List(libraryDependencies ++= scalatags.value ++ scalajsdom.value)

	// gpl3
	val neo = {
		val neoVersion = "2.1.5"
		"org.neo4j" % "neo4j" % neoVersion ::
		"org.neo4j" % "neo4j-graphviz" % neoVersion ::
		("org.neo4j" % "neo4j-kernel" % neoVersion % "test" classifier "tests") ::
		Nil
	}

	// apache 2
	val spray =
		List("spray-caching", "spray-can", "spray-client", "spray-http", "spray-httpx", "spray-routing", "spray-util")
			.map(n => "io.spray" %% n % "1.3.2")

	val akka =
		List("akka-actor", "akka-slf4j")
			.map(n => "com.typesafe.akka" %% n % "2.3.7")

	// scala license (bsdish)
	val scalaIO =
		List("scala-io-core", "scala-io-file")
			.map(n => "com.github.scala-incubator.io" %% n % "0.4.3")

	val scalaz =
		List("scalaz-core", "scalaz-concurrent")
			.map(n => "org.scalaz" %% n % "7.0.6")

	val logging =
		"ch.qos.logback" % "logback-classic" % "1.1.2" ::
		"com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" :: // apache 2
		Nil

	val commandline =
		"jline" % "jline" % "2.12" ::
		"net.sf.jopt-simple" % "jopt-simple" % "4.8" :: // mit
		Nil

	val scalatest = ("org.scalatest" %% "scalatest" % "2.2.1" % "test") :: Nil
	val scalactic = "org.scalactic" %% "scalactic" % "2.2.1" :: Nil
	val jsoup = "org.jsoup" % "jsoup" % "1.8.1" :: Nil
	val scalatags = Def.setting("com.scalatags" %%% "scalatags" % "0.4.2" :: Nil) // mit
	val rescala = "de.tuda.stg" %% "rescala" % "0.3.0" :: Nil
	val argonaut = "io.argonaut" %% "argonaut" % "6.0.4" :: Nil

	val scalajsdom = Def.setting(
		("org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6") ::
		// ("org.scala-lang.modules.scalajs" %%% "scalajs-jquery" % "0.6") ::
		Nil)

}
