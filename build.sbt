import Dependencies.*
import Settings.*
import sbtcrossproject.CrossPlugin.autoImport.crossProject

import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest

val commonSettings = Def.settings(
  scalaVersion_3,
  organization := "de.rmgk"
)

val vbundle = TaskKey[File]("vbundle", "bundles all the viscel resources")

lazy val build = project.in(file("."))
  .settings(
    fetchJSDependenciesDef,
    vbundle := {
      (app / Compile / fullOptJS).value
      val jsfile = (app / Compile / fullOptJS / artifactPath).value
      bundleStuff(
        jsfile,
        target.value.toPath.resolve("resources/static"),
        fetchJSDependencies.value,
        sourceDirectory.value,
        version.value
      )
    },
    run := {
      (server / Compile / run).dependsOn(vbundle).evaluated
    },
  )
  .aggregate(server, app)

lazy val viscel = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    commonSettings,
    resolverJitpack,
    libraryDependencies ++= jsoniterScalaAll.value ++ Seq(
      scalatags.value,
      slips.delay.value,
      slips.options.value,
      "com.github.rescala-lang.rescala" %%% "rescala" % "085d4cdbe8",
      "com.github.rescala-lang.rescala" %%% "kofre"   % "085d4cdbe8",
      loci.jsoniterScala.value,
      munitScalacheck.value
    ),
    buildInfoKeys    := Seq[BuildInfoKey](version),
    buildInfoPackage := "viscel.shared"
  )
  .jvmSettings(
    fork := true,
    libraryDependencies ++= Seq(
      scopt.value,
      jsoup.value,
      loci.wsJetty11.value,
      jetty.value,
      scribeSlf4j2.value,
    ),
    // uncomment the following to enable graal tracing to allow native image generation
    // javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/generated",
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      scalajsDom.value,
      scalatags.value,
      loci.wsWeb.value,
    ),
    scalaJSUseMainModuleInitializer := true
  )

lazy val server: Project = viscel.jvm
lazy val app: Project    = viscel.js

lazy val fetchJSDependencies = TaskKey[File]("fetchJSDependencies", "manually fetches JS dependencies")
lazy val fetchJSDependenciesDef = fetchJSDependencies := {
  val dependencies = List(
    (
      "localforage.min.js",
      "https://raw.githubusercontent.com/localForage/localForage/1.10.0/dist/localforage.min.js",
      "e762dbcebc257e9de58dbf52cd4f876537ae35ac"
    ),
    (
      "normalize.css",
      "https://raw.githubusercontent.com/csstools/normalize.css/12.0.0/normalize.css",
      "58790980bc6883e3797c7fce3e7589e6ca25a3d8"
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

def bundleStuff(
    jsfile: File,
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

  def sourcepath(p: String) = sourceDirectory.toPath.resolve(p).toFile

  IO.listFiles(sourceDirectory.toPath.resolve("main/static").toFile)
    .foreach(copyToTarget)

  val swupdated = IO.read(sourcepath("main/js/serviceworker.js"))
    .replaceAllLiterally("[inserted app cache name]", s"$version-${System.currentTimeMillis()}")
  IO.write(bundleTarget.resolve("serviceworker.js").toFile, swupdated)

  IO.listFiles(jsDpendencies).foreach(copyToTarget)

  bundleTarget.toFile
}
