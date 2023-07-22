package viscel

import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path, Paths}
import viscel.shared.Log
import viscel.store.BlobStore

import de.rmgk.options.*

object Viscel {

  val version: String = viscel.shared.BuildInfo.version

  def main(args: Array[String]): Unit = {
    run(args.toList)
    ()
  }

  def run(args: List[String]): Option[Services] =
    val res = parseArguments(args):
      Log.Main.info(s"Viscel version $version")

      val basedir = named[Path](
        "--basedir",
        "Base directory to store settings and data.",
        Paths.get("./data")
      ).value.toAbsolutePath.normalize()
      val static = named[Path]("--static", "Directory of static resources.", Paths.get("static")).value

      val staticCandidates = List(basedir.resolve(static), static.toAbsolutePath.normalize())

      val staticDir =
        staticCandidates.find(x => Files.isDirectory(x))
          .getOrElse {
            println(s"Missing optStatic resource directory, " +
              s"tried ${staticCandidates.map(c => s"»$c«").mkString(", ")}.")
            sys.exit(0)
          }

      val services = new Services(
        basedir,
        named[Path](
          "--blobdir",
          "Directory to store blobs (the images). Can be absolute, otherwise relative to basedir.",
          Paths.get("blobs")
        ).value,
        staticDir,
        named[String]("--urlprefix", "Prefix for server URLs.", "").value,
        named[String]("--interface", "Interface to bind the server to.", "0").value,
        named[Int]("--port", "Weberver listening port.", 2358).value
      )

      if (named[Boolean]("--cleanblobs", "Cleans blobs from blobstore which are no longer linked.", false).value) {
        BlobStore.cleanBlobDirectory(services)
      }

      if (named[Boolean]("--collectgarbage", "Finds unused parts in the database.", false).value) {
        services.rowStore.computeGarbage()
      }

      val noserver = named[Boolean]("--noserver", "Do not start the server.", false).value

      if (!noserver) {
        Log.Main.info(s"starting server")
        services.startServer()
      }

      if (!named[Boolean]("--nodownload", "Do not start the downloader.", false).value) {
        services.clockwork.recheckPeriodically()
        services.activateNarrationHint()
        ()
      }

      if (named[Boolean]("--shutdown", "Shutdown directly.", false).value) {
        services.terminateEverything(!noserver)
      }
      Log.Main.info(s"initialization done in ${ManagementFactory.getRuntimeMXBean.getUptime}ms")
      services

    res.printHelp()
    res.inner.toOption
}
