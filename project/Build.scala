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
		//.settings(compile in Compile <<= (compile in Compile) dependsOn (fullOptJS in(js, Compile)))
		.settings(resources in Compile += artifactPath.in(js, Compile, fullOptJS).value)
		.enablePlugins(JavaAppPackaging)
		.dependsOn(scribe)
		.settings(unmanagedSourceDirectories in Compile += baseDirectory.value / "shared")


	lazy val js = project.in(file("js"))
		.settings(name := "viscel-js")
		.enablePlugins(ScalaJSPlugin)
		.settings(Settings.common: _*)
		.settings(Libraries.js: _*)
		.settings(unmanagedSourceDirectories in Compile += baseDirectory.value / "../shared")


	lazy val scribe = ProjectRef(file("scribe"), "scribe")

}

object Settings {
	lazy val common = List(

		version := "5.11.0",
		scalaVersion := "2.11.5",
		SourceGeneration.caseCodecs,

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
				//"-Ywarn-dead-code" ::
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
		SourceGeneration.neoCodecs,

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
				|import viscel.ReplUtil
				|import viscel.narration._
				|import viscel.narration.narrators._
				|import SelectUtil._
				|import viscel.database.Implicits.NodeOps
			""".stripMargin)

}

object Libraries {


	lazy val main: List[Def.Setting[_]] = List(libraryDependencies ++= neo ++ spray ++ akka ++
		commandline ++ scalatest ++ scalactic ++ jsoup ++ shared.value)

	lazy val js: List[Def.Setting[_]] = List(libraryDependencies ++= scalajsdom.value ++ shared.value)

	lazy val shared = Def.setting(scalatags.value ++ upickle.value)

	val scribe = List("viscel" %% "scribe" % "0.1.0")

	// gpl3
	val neo = {
		val neoVersion = "2.1.7"
		Seq("kernel", "lucene-index").map(module => "org.neo4j" % s"neo4j-$module" % neoVersion)
	}

	// apache 2
	val spray =
		List("spray-caching", "spray-can", "spray-client", "spray-http", "spray-httpx", "spray-routing", "spray-util")
			.map(n => "io.spray" %% n % "1.3.2")

	val akka =
		List("akka-actor", "akka-slf4j")
			.map(n => "com.typesafe.akka" %% n % "2.3.9")

	val scalaz =
		List("scalaz-core", "scalaz-concurrent")
			.map(n => "org.scalaz" %% n % "7.0.6")

	val commandline =
		"jline" % "jline" % "2.12" ::
			"net.sf.jopt-simple" % "jopt-simple" % "4.8" :: // mit
			Nil

	val scalamacros = "org.scalamacros" %% s"quasiquotes" % "2.0.1" % "provided" :: Nil

	val scalatest = ("org.scalatest" %% "scalatest" % "2.2.4" % Test) :: Nil
	val scalactic = ("org.scalactic" %% "scalactic" % "2.2.4" exclude("org.scala-lang", "scala-reflect")) :: Nil
	val jsoup = "org.jsoup" % "jsoup" % "1.8.1" :: Nil
	val scalatags = Def.setting("com.lihaoyi" %%% "scalatags" % "0.4.5" :: Nil)
	val upickle = Def.setting("com.lihaoyi" %%% "upickle" % "0.2.6" :: Nil)

	val scalajsdom = Def.setting(
		("org.scala-js" %%% "scalajs-dom" % "0.8.0") ::
			Nil)

	val rescala = ("de.tuda.stg" %% "rescala" % "0.4.0"
		exclude("org.scala-lang", "scala-compiler")
		exclude("org.scala-lang", "scala-reflect")) :: Nil

}


object SourceGeneration {
	val caseCodecs = sourceGenerators in Compile <+= sourceManaged in Compile map { dir =>
		val file = dir / "viscel" / "generated" / "UpickleCodecs.scala"
		def sep(l: Seq[String]) = l.mkString(", ")
		val definitions = (1 to 22).map { i =>
			val nameList = 1 to i map ("n" + _)
			def types(app: String) = sep(1 to i map ("I" + _ + app))
			val writeJSs = if (i == 1) "n1 -> writeJs(a)"
			else sep(nameList.zip(1 to i).map { case (p, j) => s"$p -> writeJs(a._$j)" })
			val readUnapply = sep(nameList.zip(1 to i).map { case (p, j) => s"(`$p`, a$j)" })
			val readJSs = sep(1 to i map { j => s"readJs[I$j](a$j)" })
			val names = sep(nameList map (_ + ": String"))


			s"""def case${ i }R[T, ${ types(":R") }](read: (${ types("") }) => T)($names): R[T] = R[T] {
				 |case Js.Obj($readUnapply) => read($readJSs)
				 |}
				 |
				 |def case${ i }W[T, ${ types(":W") }](write: T => Option[(${ types("") })])($names): W[T] = W[T] { t: T =>
				 |val a = write(t).get
				 |Js.Obj($writeJSs)
				 |}
				 |
				 |def case${ i }RW[T, ${ types(":R:W") }](read: (${ types("") }) => T, write: T => Option[(${ types("") })])($names): ReaderWriter[T] = {
				 |ReaderWriter(case${ i }R(read)(${ sep(nameList) }), case${ i }W(write)(${ sep(nameList) }))
				 |}
				 |""".stripMargin

		}
		IO.write(file,
			s"""
			|package viscel.generated
			|
			|import upickle.{Js, Reader => R, Writer => W, writeJs, readJs}
			|import viscel.shared.ReaderWriter
			|import scala.Predef.ArrowAssoc
			|import scala.collection.immutable.Map
			|trait UpickleCodecs {
			|${ definitions.mkString("\n") }
			|}
			|""".stripMargin)
		Seq(file)
	}

	val neoCodecs = sourceGenerators in Compile <+= sourceManaged in Compile map { dir =>
		val file = dir / "viscel" / "generated" / "NeoCodecs.scala"
		def sep(l: Seq[String]) = l.mkString(", ")
		val definitions = (1 to 22).map { i =>
			val nameList = 1 to i map ("n" + _)
			val types = sep(1 to i map ("I" + _))
			val writeNodes = if (i == 1) "(n1, a)"
			else sep(nameList.zip(1 to i).map { case (p, j) => s"($p, a._$j)" })
			val readNodes = sep(nameList.zip(1 to i).map { case (p, j) => s"node.prop[I${ j }]($p)" })
			val names = sep(nameList map (_ + ": String"))


			s"""
			|def case${ i }RW[T, $types](label: SimpleLabel, $names)(readf: ($types) => T, writef: T => ($types)): NeoCodec[T] = new NeoCodec[T] {
			| override def read(node: Node)(implicit ntx: Ntx): T = readf($readNodes)
			| override def write(value: T)(implicit ntx: Ntx): Node = {
			|   val a = writef(value)
			|   ntx.create(label, $writeNodes)
			| }
			|}
			|""".stripMargin

		}
		IO.write(file,
			s"""
			|package viscel.generated
			|
			|import org.neo4j.graphdb.Node
			|import viscel.compat.v1.database.Implicits.NodeOps
			|import viscel.compat.v1.database.Ntx
			|import viscel.compat.v1.database.label.SimpleLabel
			|import viscel.compat.v1.database.NeoCodec
			|object NeoCodecs {
			|${ definitions.mkString("\n") }
			|}
			|""".stripMargin)
		Seq(file)
	}

}
