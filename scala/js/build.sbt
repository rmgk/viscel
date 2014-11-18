import scala.scalajs.sbtplugin.ScalaJSPlugin._
import ScalaJSKeys._

scalaJSSettings

name := "viscel-js"

version := "1"

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
		"-Yno-predef" ::
		//"-Yno-imports" ::
		"-Xfatal-warnings" ::
		"-Yinline-warnings" ::
		"-Yno-adapted-args" ::
		"-Ywarn-dead-code" ::
		"-Ywarn-nullary-override" ::
		"-Ywarn-nullary-unit" ::
		//"-Ywarn-numeric-widen" ::
		//"-Ywarn-value-discard" ::
		Nil)

libraryDependencies += "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6"

