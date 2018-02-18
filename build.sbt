
lazy val viscel = project.in(file("."))
  .settings(
    name := "viscel",
    Settings.main,
    Libraries.shared,
    Libraries.main,
    (Compile / compile) := ((Compile / compile) dependsOn (web / Compile / fastOptJS)).value,
    Compile / compile := ((compile in Compile) dependsOn (web / Assets / SassKeys.sassify)).value,
    (Compile / resources) += (web / Compile / fastOptJS / artifactPath).value,
    (Compile / resources) ++= (web / Assets / SassKeys.sassify).value,
    sharedSource,
    )
  .enablePlugins(JavaServerAppPackaging)
  .dependsOn(shared % Provided)

lazy val web = project.in(file("web"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "web",
    Settings.common,
    Libraries.shared,
    Libraries.js,
    sharedSource,
    scalaJSUseMainModuleInitializer := true,
    )
  .dependsOn(shared % Provided)
  .enablePlugins(SbtSassify)

lazy val shared = project.in(file("shared"))
  .settings(
    name := "shared",
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


    Compile / compile / scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-unchecked",
      "-feature",
      "-target:jvm-1.8",
      "-Xlint",
      "-Xfuture",
      //"-Xlog-implicits" ,
      //"-Yno-predef" ,
      //"-Yno-imports" ,
      "-Xfatal-warnings",
      //"-Yinline-warnings" ,
      "-Yno-adapted-args",
      //"-Ywarn-dead-code" ,
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      //"-Ywarn-value-discard" ,
      ),
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

    (console / initialCommands) :=
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

  lazy val shared: Def.SettingsDefinition = Seq(
    resolvers += Resolver.bintrayRepo("rmgk", "maven"),
    resolvers += Resolver.bintrayRepo("stg-tud", "maven"),
    libraryDependencies ++= Seq(
      "de.rmgk" %%% "logging" % "0.2.0",
      "de.tuda.stg" %%% "rescala" % "0.21.1",
      "com.lihaoyi" %%% "scalatags" % "0.6.7",
      ).++(Seq(
      "circe-core",
      "circe-generic",
      "circe-generic-extras",
      "circe-parser",
      ).map(n => "io.circe" %%% n % "0.9.1"))
      .map(_ exclude("org.scala-lang", "scala-reflect"))
  )

  lazy val main: Def.SettingsDefinition = libraryDependencies ++= Seq(
    "org.scalactic" %% "scalactic" % "3.0.5",
    "org.jsoup" % "jsoup" % "1.11.2",
    "com.monovore" %% "decline" % "0.4.1",
    "org.asciidoctor" % "asciidoctorj" % "1.5.6",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
    ).++(
    Seq("akka-http-core", "akka-http").map(n => "com.typesafe.akka" %% n % "10.0.11"))
    .map(_ exclude("org.scala-lang", "scala-reflect"))

  lazy val js: Def.SettingsDefinition = libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.4",
    "de.tuda.stg" %%% "rescalatags" % "0.21.0",
    "org.webjars.npm" % "purecss" % "1.0.0",
    )


}
