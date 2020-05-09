import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import Settings._
import Dependencies._

inThisBuild(scalaVersion_213)
ThisBuild / organization := "de.rmgk"

val Libraries = new {

  val rescalaVersion = "0.30.0"
  val rescala        = libraryDependencies += "de.tuda.stg" %%% "rescala" % rescalaVersion


  val shared = Def.settings(
    strictCompile, scribe, scalatags, loci.communication, circe, rescala, loci.circe, loci.wsAkka, akkaHttp
  )

  val main =
    Def.settings(strictCompile, betterFiles, decline,
                 scalatest, scalacheck, scalatestpluscheck,
                 jsoup, okHttp)


  val js: Def.SettingsDefinition = Seq(scalajsdom, normalizecss, fontawesome, scalatags,
                                       Resolvers.stg, strictCompile)
}

val fetchJSDependencies = TaskKey[File]("fetchJSDependencies",
                                        "manually fetches JS dependencies")
val fetchJSDependenciesDef = fetchJSDependencies := {
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
    val url = new URL(urlStr)
    val filepath = dependenciesTarget.resolve(name)
    val fos = Files.newOutputStream(filepath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
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

val vbundle = TaskKey[File]("vbundle", "bundles all the viscel resources")
val vbundleDef = vbundle := {
  (app / Compile / fastOptJS).value
  val jsfile = (app / Compile / fastOptJS / artifactPath).value
  val styles = (app / Assets / SassKeys.sassify).value
  val bundleTarget = target.value.toPath.resolve("resources/static")
  Files.createDirectories(bundleTarget)

  def gzipToTarget(f: File): Unit = IO.gzip(f, bundleTarget.resolve(f.name + ".gz").toFile)

  gzipToTarget(jsfile)
  gzipToTarget(jsfile.toPath.getParent.resolve(jsfile.name + ".map").toFile)
  styles.foreach(gzipToTarget)

  def sourcepath(p: String) = sourceDirectory.value.toPath.resolve(p).toFile

  val sources = IO.listFiles(sourcepath("main/vid"))
  sources.foreach { f => IO.copyFile(f, bundleTarget.resolve(f.name).toFile) }

  IO.listFiles(sourceDirectory.value.toPath.resolve("main/static").toFile)
    .foreach(gzipToTarget)


  val swupdated = IO.read(sourcepath("main/js/serviceworker.js"))
                    .replaceAllLiterally("[inserted app cache name]", System.currentTimeMillis().toString)
  IO.gzipFileOut(bundleTarget.resolve("serviceworker.js.gz").toFile){os =>
    os.write(swupdated.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  IO.listFiles(fetchJSDependencies.value).foreach(gzipToTarget)

  bundleTarget.toFile
}

val selfversion = TaskKey[File]("selfversion", "add the current version ")
val selfversionDef = selfversion := {
  val versiondir = target.value.toPath.resolve("resources/bundled")
  Files.createDirectories(versiondir)
  val versiontarget = versiondir.resolve("version").toFile
  IO.write(versiontarget, version.value)
  versiontarget
}


lazy val viscel = project
                  .in(file("."))
                  .settings(
                    name := "viscel",
                    fork := true,
                    Libraries.main,
                    fetchJSDependenciesDef,
                    vbundleDef,
                    (Compile / compile) := ((Compile / compile) dependsOn vbundle).value,
                    publishLocal := publishLocal.dependsOn(sharedJVM / publishLocal).value,
                    selfversionDef,
                    (Compile / managedResources) += selfversion.value
                  )
                  .enablePlugins(JavaServerAppPackaging)
                  .dependsOn(sharedJVM)

//  experimental graalvm options, do not work :(
                    // javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
                    // graalVMNativeImageOptions ++= List(
                    //   "--initialize-at-build-time",
                    //   "--report-unsupported-elements-at-runtime",
                    // ),
                  // .enablePlugins(GraalVMNativeImagePlugin)


lazy val app = project.in(file("app"))
               .enablePlugins(ScalaJSPlugin)
               .settings(
                 name := "app",
                 Libraries.js,
                 scalaJSUseMainModuleInitializer := true
               )
               .dependsOn(sharedJS)
               .enablePlugins(SbtSassify)

lazy val shared = crossProject(JSPlatform, JVMPlatform).in(file("shared"))
                  .settings(
                    name := "viscel-shared",
                    Libraries.shared,
                    )
lazy val sharedJVM = shared.jvm
lazy val sharedJS = shared.js

lazy val benchmarks = project.in(file("benchmarks"))
                      .settings(name := "benchmarks")
                      .enablePlugins(JmhPlugin)
                      .dependsOn(viscel)
