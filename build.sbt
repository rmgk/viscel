import Dependencies._
import Settings._
import sbtcrossproject.CrossPlugin.autoImport.crossProject

import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest

val commonSettings = Def.settings(
  scalaVersion_3,
  organization := "de.rmgk"
)

lazy val viscelPackage = taskKey[File]("calls graalvm native image")

lazy val viscelBundle = project.in(file(".")).settings(
  commonSettings,
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

lazy val viscel = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("code"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "viscel",
    commonSettings,
    jitpackResolver,
    libraryDependencies ++= jsoniterScalaAll.value ++ Seq(
      scribe.value,
      scalatags.value,
      scribeSlf4j.value,
      quicklens.value,
      slips.delay.value,
      "com.github.rescala-lang.rescala" %%% "rescala" % "6d9019e946",
      "com.github.rescala-lang.rescala" %%% "kofre"   % "6d9019e946",
      loci.jsoniterScala.value,
    ),
    buildInfoKeys    := Seq[BuildInfoKey](version),
    buildInfoPackage := "viscel.shared"
  )
  .jvmSettings(
    fork := true,
    libraryDependencies ++= Seq(
      betterFiles.value.cross(CrossVersion.for3Use2_13),
      scopt.value,
      scalatest.value,
      scalacheck.value,
      scalatestpluscheck.value,
      jsoup.value,
      okHttp.value,
      loci.wsJetty.value,
      jetty.value
    ),
    // uncomment the following to enable graal tracing to allow native image generation
    // javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
    nativeImageVersion := "22.1.0",
    nativeImageJvm     := "graalvm-java17",
    nativeImageOptions ++= List(
      "--no-fallback",
      "-H:EnableURLProtocols=http,https",
    ),
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      scalajsDom.value,
      scalatags.value,
      loci.wsWeb.value,
    ),
    scalaJSUseMainModuleInitializer := true
  )
  .enablePlugins(NativeImagePlugin)

lazy val server = viscel.jvm
lazy val app    = viscel.js

lazy val benchmarks = project.in(file("benchmarks"))
  .settings(name := "benchmarks", commonSettings)
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

  def copyToTarget(f: File): Unit = // IO.gzip(f, bundleTarget.resolve(f.name + ".gz").toFile)
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
