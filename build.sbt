lazy val viscel = project.in(file("."))
	.settings(
		name := "viscel",
		Settings.main,
		Libraries.shared,
		Libraries.main,
		(Compile / compile) := ((Compile / compile) dependsOn (js / Compile / fullOptJS)).value,
		(Compile / resources) += (js / Compile / fullOptJS / artifactPath).value,
		sharedSource,
	)
	.enablePlugins(JavaServerAppPackaging)
	.dependsOn(shared % Provided)

lazy val js = project.in(file("js"))
	.enablePlugins(ScalaJSPlugin)
	.settings(
		name := "viscel-js",
		Settings.common,
		Libraries.shared,
		Libraries.js,
		sharedSource,
		scalaJSUseMainModuleInitializer := true,
	)
	.dependsOn(shared % Provided)

lazy val shared = project.in(file("shared"))
	.settings(
		name := "viscel-shared",
		Settings.common,
		Libraries.shared,
	)

lazy val sharedSource = (Compile / unmanagedSourceDirectories) += (shared / Compile / scalaSource).value


lazy val Settings = new {

	lazy val common: sbt.Def.SettingsDefinition = Seq(

		version := "7.2",
		scalaVersion := "2.12.4",

		maxErrors := 10,
		shellPrompt := { state => Project.extract(state).currentRef.project + "> " },


		Compile / compile / scalacOptions ++= Seq (
			"-deprecation" ,
			"-encoding" , "UTF-8" ,
			"-unchecked" ,
			"-feature" ,
			"-target:jvm-1.8" ,
			"-Xlint" ,
			"-Xfuture" ,
			//"-Xlog-implicits" ,
			//"-Yno-predef" ,
			//"-Yno-imports" ,
			"-Xfatal-warnings" ,
			//"-Yinline-warnings" ,
			"-Yno-adapted-args" ,
			//"-Ywarn-dead-code" ,
			"-Ywarn-nullary-override" ,
			"-Ywarn-nullary-unit" ,
			"-Ywarn-numeric-widen" ,
			//"-Ywarn-value-discard" ,
		),

		resolvers += Resolver.bintrayRepo("stg-tud", "maven"),
	)

	lazy val main = common ++ Seq(

		fork := true,

		javaOptions ++= Seq(
			"-verbose:gc",
			"-XX:+PrintGCDetails",
			//"-Xverify:none",
			//"-server",
			//"-Xms16m",
			//"-Xmx256m",
			//"-Xss1m",
			//"-XX:MinHeapFreeRatio=5",
			//"-XX:MaxHeapFreeRatio=10",
			//"-XX:NewRatio=12",
			//"-XX:+UseSerialGC",
			//"-XX:+UseParallelGC",
			//"-XX:+UseParallelOldGC",
			//"-XX:+UseConcMarkSweepGC",
			//"-XX:+PrintTenuringDistribution",
		),

		(console / initialCommands)  :=
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

	lazy val shared: Def.SettingsDefinition =  Seq(circe, scalatags, rescala)

	lazy val main: Def.SettingsDefinition = Seq(akkaHTTP, circe, scalactic, jsoup, decline, asyncAwait)

	lazy val js: Def.SettingsDefinition = Seq(scalajsdom, rescalatags)

	val jsoup = libraryDependencies += "org.jsoup" % "jsoup" % "1.11.2"

	val akkaHTTP = libraryDependencies ++= Seq("akka-http-core", "akka-http").map(n => "com.typesafe.akka" %% n % "10.0.11")

	val decline = libraryDependencies += "com.monovore" %% "decline" % "0.4.1" exclude("org.scala-lang", "scala-reflect")

	val scalactic = libraryDependencies +=("org.scalactic" %% "scalactic" % "3.0.4" exclude("org.scala-lang", "scala-reflect"))

	val scalatags = libraryDependencies +=("com.lihaoyi" %%% "scalatags" % "0.6.7" )

	val scalajsdom = libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.4"

	val rescala = libraryDependencies += "de.tuda.stg" %%% "rescala" % "0.21.0"
	val rescalatags = libraryDependencies += "de.tuda.stg" %%% "rescalatags" % "0.21.0"

	val circe = libraryDependencies ++= Seq(
		"circe-core",
		"circe-generic",
		"circe-generic-extras",
		"circe-parser",
	).map(n => "io.circe" %%% n % "0.9.1" exclude("org.scala-lang", "scala-reflect"))

	val asyncAwait = libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.6" % Provided

}
