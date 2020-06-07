import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest

import Dependencies._
import Settings._
import sbtcrossproject.CrossPlugin.autoImport.crossProject

def lociRef(name: String) = ProjectRef(uri("git://github.com/scala-loci/scala-loci.git#77944c620bbeff3e97ea8aa40d8af6b8838ec422"), name)

lazy val lociJavalinJVM = lociRef("lociCommunicatorWsJavalinJVM")
lazy val lociJavalinJS  = lociRef("lociCommunicatorWsJavalinJS")


inThisBuild(scalaVersion_213)
ThisBuild / organization := "de.rmgk"

ThisBuild / Compile / doc / sources := Seq.empty
ThisBuild / Compile / packageDoc / publishArtifact := false

lazy val nativeImage = taskKey[File]("calls graalvm native image")


lazy val viscel = project.in(file(".")).settings(
  vbundleDef,
  normalizecss,
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


lazy val code = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("code"))
  .settings(
    name := "viscel",
    strictCompile, scribe, scalatags,
    scribeSlf4j, Resolvers.stg,
    libraryDependencies += "de.tuda.stg" %%% "rescala" % "0.30.0",
    //libraryDependencies += "io.lemonlabs" %%% "scala-uri" % "2.2.2",
    jsoniter,
    Compile / sourceGenerators += Def.task {
      val file      = (Compile / sourceManaged).value / "viscel" / "shared" / "Version.scala"
      val outstring = s"""package viscel.shared; object Version { val str = "${version.value}"}"""
      val current   = try IO.read(file) catch {case _: Throwable => ""}
      if (current != outstring) IO.write(file, outstring)
      Seq(file)
    }
    )
  .jvmSettings(
    fork := true,
    strictCompile, betterFiles, decline,
    scalatest, scalacheck, scalatestpluscheck,
    jsoup, okHttp, javalin,
    jsoniter,
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
    else Nil
    )
  .jsSettings(
    scalajsdom, scalatags,
    Resolvers.stg, strictCompile,
    scalaJSUseMainModuleInitializer := true,
    jsoniter
    )
  .enablePlugins(GraalVMNativeImagePlugin)
  .enablePlugins(JavaServerAppPackaging)

lazy val server = code.jvm.dependsOn(lociJavalinJVM)
lazy val app    = code.js.dependsOn(lociJavalinJS)








lazy val benchmarks = project.in(file("benchmarks"))
                             .settings(name := "benchmarks")
                             .enablePlugins(JmhPlugin)
                             .dependsOn(code.jvm)








lazy val fetchJSDependencies    = TaskKey[File]("fetchJSDependencies",
                                                "manually fetches JS dependencies")
lazy val fetchJSDependenciesDef = fetchJSDependencies := {
  val dependencies = List(
    ("localforage.min.js",
      "https://cdn.jsdelivr.net/npm/localforage@1.7.3/dist/localforage.min.js",
      "c071378e565cc4e2c6c34fca6f3a74f32c3d96cb")
    )

  val sha1digester: MessageDigest = MessageDigest.getInstance("SHA-1")

  def sha1hex(b: Array[Byte]): String =
    sha1digester.clone().asInstanceOf[MessageDigest].digest(b)
                .map { h => f"$h%02x" }.mkString

  val dependenciesTarget = target.value.toPath.resolve("resources/jsdependencies")
  Files.createDirectories(dependenciesTarget)
  dependencies.map { case (name, urlStr, sha1) =>
    val url      = new URL(urlStr)
    val filepath = dependenciesTarget.resolve(name)
    val fos      = Files.newOutputStream(filepath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    try IO.transferAndClose(url.openStream(), fos) finally fos.close()
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
  bundleStuff(jsfile, styles, target.value.toPath.resolve("resources/static"), fetchJSDependencies.value, sourceDirectory.value, version.value)
}

lazy val vbundle = TaskKey[File]("vbundle", "bundles all the viscel resources")

def bundleStuff(jsfile: File, styles: Seq[File], bundleTarget: Path, jsDpendencies: File, sourceDirectory: File, version: String): File = {
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
