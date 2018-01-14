import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._


lazy val viscel = project.in(file("."))
	.settings(name := "viscel")
	.settings(Settings.main)
	.settings(Libraries.main)
	.settings(compile in Compile := ((compile in Compile) dependsOn (fullOptJS in(js, Compile))).value)
	.settings(resources in Compile += artifactPath.in(js, Compile, fullOptJS).value)
	.enablePlugins(JavaServerAppPackaging)
	.dependsOn(shared % Provided)
	.settings(sharedSource)


lazy val js = project.in(file("js"))
	.settings(name := "viscel-js")
	.enablePlugins(ScalaJSPlugin)
	.settings(Settings.common)
	.settings(Libraries.js)
	.settings(scalaJSUseMainModuleInitializer := true)
	.dependsOn(shared % Provided)
	.settings(sharedSource)

lazy val shared = project.in(file("shared"))
	.settings(name := "viscel-shared")
	.settings(Settings.common: _*)
	.settings(libraryDependencies ++= Libraries.shared.value)


lazy val sharedSource = unmanagedSourceDirectories in Compile += (scalaSource in (shared, Compile)).value


lazy val Settings = new {

	lazy val common = List(

		version := "7.2.0",
		scalaVersion := "2.12.4",

		maxErrors := 10,
		shellPrompt := { state => Project.extract(state).currentRef.project + "> " },


		scalacOptions ++=
			"-deprecation" ::
			"-encoding" :: "UTF-8" ::
			"-unchecked" ::
			"-feature" ::
			"-target:jvm-1.8" ::
			"-Xlint" ::
			"-Xfuture" ::
			//"-Xlog-implicits" ::
			//"-Yno-predef" ::
			//"-Yno-imports" ::
			"-Xfatal-warnings" ::
			//"-Yinline-warnings" ::
			"-Yno-adapted-args" ::
			//"-Ywarn-dead-code" ::
			"-Ywarn-nullary-override" ::
			"-Ywarn-nullary-unit" ::
			"-Ywarn-numeric-widen" ::
			//"-Ywarn-value-discard" ::
			Nil,

		resolvers ++=
			Resolver.bintrayRepo("stg-tud", "maven") ::
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

lazy val Libraries = new {

	lazy val main: Def.Setting[_] = libraryDependencies ++=
		akkaHTTP ::: jline ::: jopt  ::: scalactic ::: shared.value ::: circe.value ::: jsoup ::: rescala.value

	lazy val js: Def.Setting[_] = libraryDependencies ++=
		scalajsdom.value ::: shared.value ::: rescala.value ::: rescalatags.value

	lazy val shared: Def.Initialize[List[ModuleID]] = Def.setting(
		scalatags.value ::: circe.value)

	val jsoup = "org.jsoup" % "jsoup" % "1.11.2" :: Nil

	val akkaHTTP = List("akka-http-core", "akka-http").map(n => "com.typesafe.akka" %% n % "10.0.11")

	val jline = "jline" % "jline" % "2.14.5" :: Nil

	val jopt = "net.sf.jopt-simple" % "jopt-simple" % "5.0.4" :: Nil

	val scalactic = ("org.scalactic" %% "scalactic" % "3.0.4" exclude("org.scala-lang", "scala-reflect")) :: Nil

	val scalatags = Def.setting("com.lihaoyi" %%% "scalatags" % "0.6.7" :: Nil)

	val scalajsdom = Def.setting(("org.scala-js" %%% "scalajs-dom" % "0.9.1") :: Nil)

	val rescala = Def.setting(("de.tuda.stg" %%% "rescala" % "0.20.0") :: Nil)
	val rescalatags = Def.setting(("de.tuda.stg" %%% "rescalatags" % "0.20.0") :: Nil)

	val circe = Def.setting(List(
		"io.circe" %%% "circe-core",
		"io.circe" %%% "circe-generic",
		"io.circe" %%% "circe-generic-extras",
		"io.circe" %%% "circe-parser"
	).map(_ % "0.9.0"))

}
