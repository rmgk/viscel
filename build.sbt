import Dependencies._
import Settings._
import sbtcrossproject.CrossPlugin.autoImport.crossProject

import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest

// def rescalaRef(name: String) =
//   ProjectRef(uri("git://github.com/rescala-lang/REScala.git#931752b93e53ecd6fd4849605b59bd0fd0ae5792"), name)

// lazy val rescalaJS = rescalaRef("rescalaJS")
// lazy val rescalaJVM = rescalaRef("rescalaJVM")

// def lociRef(name: String) =
//   ProjectRef(file("../loci"), name)
//
// lazy val lociJavalinJVM = lociRef("lociCommunicatorWsJavalinJVM")
// lazy val lociJettyJVM = lociRef("lociCommunicatorWsJettyJVM")
// lazy val lociJavalinJS  = lociRef("lociCommunicatorWsJavalinJS")

inThisBuild(scalaVersion_213)
ThisBuild / organization := "de.rmgk"

lazy val viscelPackage = taskKey[File]("calls graalvm native image")

lazy val viscelBundle = project.in(file(".")).settings(
  vbundleDef,
  libraryDependencies += normalizecss.value,
  viscelPackage := {
    (vbundle).value
    (server / nativeImage).value
  },
  run := {
    (server / Compile / run).dependsOn(vbundle).evaluated
  },
  fetchJSDependenciesDef,
)
  .enablePlugins(SbtSassify)
  .aggregate(app, server)

val lociVersion = "bc2a42fc85af44cfa583d79d7cdd128788faf586"

lazy val viscel = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("code"))
  .settings(
    name := "viscel",
    strictCompile,
    Resolvers.stg,
    resolvers += "jitpack" at "https://jitpack.io",
    libraryDependencies ++= jsoniterScalaAll.value ++ Seq(
      scribe.value,
      scalatags.value,
      scribeSlf4j.value,
      "com.github.rescala-lang.rescala" %%% "rescala" % "0923d1786b",
      "com.github.scala-loci.scala-loci" %%% "scala-loci-communicator-ws-jetty" % lociVersion,
      "com.github.scala-loci.scala-loci" %%% "scala-loci-serializer-jsoniter-scala" % lociVersion,
      "org.eclipse.jetty" % "jetty-rewrite" % "9.4.44.v20210927",
    ),
    Compile / sourceGenerators += Def.task {
      val file      = (Compile / sourceManaged).value / "viscel" / "shared" / "Version.scala"
      val outstring = s"""package viscel.shared; object Version { val str = "${version.value}"}"""
      val current =
        try IO.read(file)
        catch { case _: Throwable => "" }
      if (current != outstring) IO.write(file, outstring)
      Seq(file)
    }
  )
  .jvmSettings(
    fork := true,
    libraryDependencies ++= Seq(
      betterFiles.value,
      decline.value,
      scalatest.value,
      scalacheck.value,
      scalatestpluscheck.value,
      jsoup.value,
      okHttp.value,
    ),
    //  experimental graalvm options
    javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
    nativeImageVersion := "21.2.0",
    nativeImageJvm := "graalvm-java11",
    // nativeImageInstalled := true,
    nativeImageOptions ++= List(
      "--allow-incomplete-classpath",
      "--no-fallback",
      //"--report-unsupported-elements-at-runtime",
      // "-H:+ReportExceptionStackTraces",
      "-H:EnableURLProtocols=http,https",
      // "--enable-all-security-services",
      //"-H:+JNI",
      //"-H:+RemoveSaturatedTypeFlows",
      //"--initialize-at-build-time",
      //"--initialize-at-run-time=scala.util.Random,scala.util.Random$"
    ),
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      scalajsDom.value,
      scalatags.value,
      "com.github.scala-loci.scala-loci" %%% "scala-loci-communicator-ws-javalin" % lociVersion,
    ),
    scalaJSUseMainModuleInitializer := true
  )
  .enablePlugins(NativeImagePlugin)

lazy val server = viscel.jvm
lazy val app    = viscel.js

lazy val benchmarks = project.in(file("benchmarks"))
  .settings(name := "benchmarks")
  .enablePlugins(JmhPlugin)
  .dependsOn(viscel.jvm)

lazy val fetchJSDependencies = TaskKey[File]("fetchJSDependencies", "manually fetches JS dependencies")
lazy val fetchJSDependenciesDef = fetchJSDependencies := {
  val dependencies = List(
    (
      "localforage.min.js",
      "https://cdn.jsdelivr.net/npm/localforage@1.9.0/dist/localforage.min.js",
      "fb6cb922fef52761a894f2898af7bb4b4706143d"
    )
  )

  val sha1digester: MessageDigest = MessageDigest.getInstance("SHA-1")

  def sha1hex(b: Array[Byte]): String =
    sha1digester.clone().asInstanceOf[MessageDigest].digest(b)
      .map { h => f"$h%02x" }.mkString

  val dependenciesTarget = target.value.toPath.resolve("resources/jsdependencies")
  Files.createDirectories(dependenciesTarget)
  dependencies.map {
    case (name, urlStr, sha1) =>
      val url      = new URL(urlStr)
      val filepath = dependenciesTarget.resolve(name)
      val fos      = Files.newOutputStream(filepath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      try IO.transferAndClose(url.openStream(), fos)
      finally fos.close()
      val csha1 = sha1hex(Files.readAllBytes(filepath))
      if (sha1 != csha1) {
        Files.delete(filepath)
        throw new AssertionError(s"sha1 of »$urlStr« did not match »$sha1«")
      }
      filepath
  }

  dependenciesTarget.toFile
}

lazy val vbundleDef = vbundle := {
  (app / Compile / fullOptJS).value
  val jsfile = (app / Compile / fullOptJS / artifactPath).value
  val styles = (Assets / SassKeys.sassify).value
  bundleStuff(
    jsfile,
    styles,
    target.value.toPath.resolve("resources/static"),
    fetchJSDependencies.value,
    sourceDirectory.value,
    version.value
  )
}

lazy val vbundle = TaskKey[File]("vbundle", "bundles all the viscel resources")

def bundleStuff(
    jsfile: File,
    styles: Seq[File],
    bundleTarget: Path,
    jsDpendencies: File,
    sourceDirectory: File,
    version: String
): File = {
  Files.createDirectories(bundleTarget)

  def copyToTarget(f: File): Unit = //IO.gzip(f, bundleTarget.resolve(f.name + ".gz").toFile)
    Files.copy(f.toPath, bundleTarget.resolve(f.name), StandardCopyOption.REPLACE_EXISTING)

  copyToTarget(jsfile)
  copyToTarget(jsfile.toPath.getParent.resolve(jsfile.name + ".map").toFile)
  styles.foreach(copyToTarget)

  def sourcepath(p: String) = sourceDirectory.toPath.resolve(p).toFile

  IO.listFiles(sourceDirectory.toPath.resolve("main/static").toFile)
    .foreach(copyToTarget)

  val swupdated = IO.read(sourcepath("main/js/serviceworker.js"))
    .replaceAllLiterally("[inserted app cache name]", s"$version-${System.currentTimeMillis()}")
  IO.write(bundleTarget.resolve("serviceworker.js").toFile, swupdated)

  IO.listFiles(jsDpendencies).foreach(copyToTarget)

  bundleTarget.toFile
}

// fix some linting nonsense
Global / excludeLintKeys += nativeImageVersion
Global / excludeLintKeys += nativeImageJvm
