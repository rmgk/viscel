import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._

object Build extends sbt.Build {

	lazy val viscel = project.in(file("."))
		.settings(name := "viscel")
		.settings(Settings.main: _*)
		.settings(Libraries.main: _*)
		.settings(compile in Compile <<= (compile in Compile) dependsOn (fullOptJS in(js, Compile)))
		.settings(resources in Compile += artifactPath.in(js, Compile, fullOptJS).value)
		.enablePlugins(JavaAppPackaging)
		.dependsOn(shared % Provided)
		.settings(Settings.sharedSource)


	lazy val js = project.in(file("js"))
		.settings(name := "viscel-js")
		.enablePlugins(ScalaJSPlugin)
		.settings(Settings.common: _*)
		.settings(Libraries.js: _*)
		.dependsOn(shared % Provided)
		.settings(Settings.sharedSource)

	lazy val shared = project.in(file("shared"))
		.settings(name := "viscel-shared")
		.settings(Settings.common: _*)
		.settings(libraryDependencies ++= Libraries.shared.value)

}

object Settings {

	lazy val sharedSource = unmanagedSourceDirectories in Compile += (scalaSource in (Build.shared, Compile)).value

	lazy val common = List(

		version := "7.0.0",
		scalaVersion := "2.11.8",

		scalacOptions ++=
			"-deprecation" ::
			"-encoding" :: "UTF-8" ::
			"-unchecked" ::
			"-feature" ::
			"-target:jvm-1.7" ::
			"-Xlint" ::
			"-Xfuture" ::
			//"-Xlog-implicits" ::
			//"-Yno-predef" ::
			//"-Yno-imports" ::
			"-Xfatal-warnings" ::
			"-Yinline-warnings" ::
			"-Yno-adapted-args" ::
			//"-Ywarn-dead-code" ::
			"-Ywarn-nullary-override" ::
			"-Ywarn-nullary-unit" ::
			//"-Ywarn-numeric-widen" ::
			//"-Ywarn-value-discard" ::
			Nil,

		resolvers ++=
			("Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/") ::
			("Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/") ::
			("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/") ::
			Resolver.bintrayRepo("rmgk", "maven") ::
			Nil)

	lazy val main: List[Def.Setting[_]] = common ++ List(

		fork := true,

		javaOptions ++=
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
			Nil,

		initialCommands in console :=
			"""import akka.actor.{ ActorSystem, Props, Actor }
				|import akka.io.IO
				|import akka.util.Timeout
				|import org.jsoup._
				|import scala.collection.JavaConversions._
				|import scala.concurrent._
				|import scala.concurrent.duration._
				|import scala.concurrent.ExecutionContext.Implicits.global
				|import viscel._
				|import viscel.server._
				|import viscel.store._
				|import viscel.ReplUtil
				|import viscel.narration._
				|import viscel.narration.narrators._
			""".stripMargin)

}

object Libraries {

	lazy val main: List[Def.Setting[_]] = List(libraryDependencies ++=
		akkaHTTP ::: jline ::: jopt  ::: scalactic ::: shared.value ::: upickle.value ::: jsoup ::: rescala.value)

	lazy val js: List[Def.Setting[_]] = List(libraryDependencies ++=
		scalajsdom.value ::: shared.value ::: rescala.value ::: rescalatags.value)

	lazy val shared: Def.Initialize[List[ModuleID]] = Def.setting(
		scalatags.value ::: upickle.value)

	val jsoup = "org.jsoup" % "jsoup" % "1.9.2" :: Nil

	val akkaHTTP = List("akka-http-core", "akka-http-experimental").map(n => "com.typesafe.akka" %% n % "2.4.10")

	val jline = "jline" % "jline" % "2.14.2" :: Nil

	val jopt = "net.sf.jopt-simple" % "jopt-simple" % "5.0.2" :: Nil

	val scalactic = ("org.scalactic" %% "scalactic" % "3.0.0" exclude("org.scala-lang", "scala-reflect")) :: Nil

	val scalatags = Def.setting("com.lihaoyi" %%% "scalatags" % "0.6.0" :: Nil)

	val upickle = Def.setting("com.lihaoyi" %%% "upickle" % "0.4.2" :: Nil)

	val scalajsdom = Def.setting(("org.scala-js" %%% "scalajs-dom" % "0.9.0") :: Nil)

	val rescala = Def.setting(("de.tuda.stg" %%% "rescala" % "0.18.0") :: Nil)
	val rescalatags = Def.setting(("de.tuda.stg" %%% "rescalatags" % "0.18.0") :: Nil)

}
