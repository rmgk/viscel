import sbt.Keys._
import sbt._

object Build extends sbt.Build {

	lazy val crawl = project.in(file("."))
		.settings(name := "viscelcrawl")
		.settings(Settings.main: _*)
		.settings(Libraries.main: _*)
		.settings(SourceGeneration.neoCodecs)


}

object Settings {
	lazy val main = List(

		version := "0.1.0",
		scalaVersion := "2.11.5",

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
}

object Libraries {


	lazy val main: List[Def.Setting[_]] = List(libraryDependencies ++= neo ++ spray ++ akka ++
		 scalatest ++ scalactic ++ jsoup)


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


	val scalatest = ("org.scalatest" %% "scalatest" % "2.2.4" % Test) :: Nil
	val scalactic = ("org.scalactic" %% "scalactic" % "2.2.4" exclude("org.scala-lang", "scala-reflect")) :: Nil
	val jsoup = "org.jsoup" % "jsoup" % "1.8.1" :: Nil

}


object SourceGeneration {
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
			|import viscel.database.Implicits.NodeOps
			|import viscel.database.Ntx
			|import viscel.database.label.SimpleLabel
			|import viscel.database.NeoCodec
			|object NeoCodecs {
			|${ definitions.mkString("\n") }
			|}
			|""".stripMargin)
		Seq(file)
	}

}
