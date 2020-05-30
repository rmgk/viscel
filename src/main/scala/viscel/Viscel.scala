package viscel

import java.lang.management.ManagementFactory
import java.nio.file.{Path, Paths}

import better.files.File
import cats.implicits._
import com.monovore.decline.{Command, Opts}
import viscel.shared.Log
import viscel.store.BlobStore

import scala.collection.immutable.ArraySeq

object Viscel {

  val args =
    (Opts.option[Path](long = "basedir", metavar = "directory", help = "Base directory to store settings and data.")
         .withDefault(Paths.get("./data/")),
      Opts.option[Int](long = "port", help = "Weberver listening port.").withDefault(2358),
      Opts.option[String](long = "interface", metavar = "interface", help = "Interface to bind the server on.")
          .withDefault("0"),
      Opts.flag(long = "noserver", help = "Do not start the server.").orTrue,
      Opts.flag(long = "nodownload", help = "Do not start the downloader.").orTrue,
      Opts.flag(long = "cleanblobs", help = "Cleans blobs from blobstore which are no longer linked.").orFalse,
      Opts.option[Path](long = "blobdir", metavar = "directory",
                        help = "Directory to store blobs (the images). Can be absolute, otherwise relative to basedir.")
          .withDefault(Paths.get("./blobs/")),
      Opts.flag(long = "shutdown", help = "Shutdown directly.").orFalse,
      Opts.option[Path](long = "static", metavar = "directory", help = "Directory of static resources.")
          .withDefault(Paths.get("./static/")),
      Opts.option[String](long = "urlprefix", metavar = "string", help = "Prefix for server URLs.").withDefault(""),
      Opts.flag(long = "collectgarbage", help = "Finds unused parts in the database.").orFalse,
      )

  val command: Command[Services] = Command(name = "viscel", header = "Start viscel!") {
    args.mapN {
      (optBasedir, port, interface, server, core, cleanblobs, optBlobdir, shutdown, optStatic, urlPrefix, collectDbGarbage) =>

        val staticCandidates = List(File(optBasedir.resolve(optStatic)),
                                    File(optStatic))

        val staticDir =
          staticCandidates.find(_.isDirectory)
                          .getOrElse {
                            println(s"Missing static resource directory, " +
                                    s"tried ${staticCandidates.map(c => s"»$c«").mkString(", ")}.")
                            sys.exit(0)
                          }

        val services = new Services(optBasedir, optBlobdir, staticDir.path, urlPrefix, interface, port)

        if (cleanblobs) {
          BlobStore.cleanBlobDirectory(services)
        }

        if (collectDbGarbage) {
          services.rowStore.computeGarbage()
        }

        if (server) {
          Log.Main.info(s"starting server")
          services.startServer()
        }

        if (core) {
          services.clockwork.recheckPeriodically()
          services.activateNarrationHint()
        }

        if (shutdown) {
          services.terminateEverything(server)
        }
        Log.Main.info(s"initialization done in ${ManagementFactory.getRuntimeMXBean.getUptime}ms")
        services
    }
  }

  val version: String = viscel.shared.Version.str

  def main(args: Array[String]): Unit = run(ArraySeq.unsafeWrapArray(args):_*)

  def run(args: String*): Services = {
    Log.Main.info(s"Viscel version $version")
    command.parse(args) match {
      case Left(help)     =>
        println(help)
        sys.exit(0)
      case Right(service) => service
    }
  }
}
