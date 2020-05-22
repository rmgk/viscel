import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import sbt._
import sbt.Keys._

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

object AdvancedBuild {

  val fetchJSDependencies    = TaskKey[File]("fetchJSDependencies",
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

  val vbundle    = TaskKey[File]("vbundle", "bundles all the viscel resources")

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
}
