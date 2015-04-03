import sbt.Keys._
import sbt._

object Build extends sbt.Build {

	lazy val selection = project.in(file("."))
		.settings(
			name := "selection",
			version := "0.1.0",
			organization := "viscel",
			scalaVersion := "2.11.6",

			scalacOptions ++= (
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
				Nil),
			libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.4"  exclude("org.scala-lang", "scala-reflect"),
			libraryDependencies += "org.jsoup" % "jsoup" % "1.8.1"
		)

}
