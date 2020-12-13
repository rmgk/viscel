import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest

import Dependencies._
import Settings._
import sbtcrossproject.CrossPlugin.autoImport.crossProject

def rescalaRef(name: String) =
  ProjectRef(uri("git://github.com/rescala-lang/REScala.git#931752b93e53ecd6fd4849605b59bd0fd0ae5792"), name)

lazy val rescalaJS = rescalaRef("rescalaJS")
lazy val rescalaJVM = rescalaRef("rescalaJVM")

def lociRef(name: String) =
  ProjectRef(uri("git://github.com/scala-loci/scala-loci.git#55433d73db8c49fd8b4292e5b9f20fe535e761c0"), name)

lazy val lociJavalinJVM = lociRef("lociCommunicatorWsJavalinJVM")
lazy val lociJavalinJS  = lociRef("lociCommunicatorWsJavalinJS")

inThisBuild(scalaVersion_213)
ThisBuild / organization := "de.rmgk"

lazy val nativeImage = taskKey[File]("calls graalvm native image")

lazy val viscelBundle = project.in(file(".")).settings(
  vbundleDef,
  libraryDependencies += normalizecss.value,
  nativeImage := {
    (vbundle).value
    (server / GraalVMNativeImage / packageBin).value
  },
  run := {
    (server / Compile / run).dependsOn(vbundle).evaluated
  },
  fetchJSDependenciesDef,
)
  .enablePlugins(SbtSassify)
  .aggregate(app, server)

lazy val viscel = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("code"))
  .settings(
    name := "viscel",
    strictCompile,
    Resolvers.stg,
    libraryDependencies ++= jsoniterScalaAll.value ++ Seq(
      scribe.value,
      scalatags.value,
      scribeSlf4j.value,
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
    strictCompile,
    libraryDependencies ++= jsoniterScalaAll.value ++ Seq(
      betterFiles.value,
      decline.value,
      scalatest.value,
      scalacheck.value,
      scalatestpluscheck.value,
      jsoup.value,
      okHttp.value,
      javalin.value,
    ),
    //  experimental graalvm options
    // javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
    graalVMNativeImageOptions ++= List(
      "--allow-incomplete-classpath",
      "--no-fallback",
      //"--report-unsupported-elements-at-runtime",
      "--initialize-at-build-time",
      // "-H:+ReportExceptionStackTraces",
      "-H:EnableURLProtocols=http,https",
      // "--enable-all-security-services",
      "-H:+JNI",
      "-H:+RemoveSaturatedTypeFlows"
    ),
    if (sys.env.contains("GRAALVM_NATIVE_IMAGE_PATH"))
      graalVMNativeImageCommand := sys.env("GRAALVM_NATIVE_IMAGE_PATH")
    else Nil,
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => Seq("org.graalvm.nativeimage" % "svm" % "20.3.0" % "compile-internal") // or "provided", but it is required only in compile-time
      case _ => Seq()
     })
  )
  .jsSettings(
    libraryDependencies ++= jsoniterScalaAll.value ++ Seq(
    scalajsDom.value,
    scalatags.value,
    ),
    strictCompile,
    scalaJSUseMainModuleInitializer := true
  )
  .enablePlugins(GraalVMNativeImagePlugin)
  .enablePlugins(JavaServerAppPackaging)

lazy val server = viscel.jvm.dependsOn(rescalaJVM, lociJavalinJVM)
lazy val app    = viscel.js.dependsOn(rescalaJS, lociJavalinJS)

lazy val benchmarks = project.in(file("benchmarks"))
  .settings(name := "benchmarks")
  .enablePlugins(JmhPlugin)
  .dependsOn(viscel.jvm)

lazy val fetchJSDependencies = TaskKey[File]("fetchJSDependencies", "manually fetches JS dependencies")
lazy val fetchJSDependenciesDef = fetchJSDependencies := {
  val dependencies = List(
    (
      "localforage.min.js",
      "https://cdn.jsdelivr.net/npm/localforage@1.7.3/dist/localforage.min.js",
      "c071378e565cc4e2c6c34fca6f3a74f32c3d96cb"
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
