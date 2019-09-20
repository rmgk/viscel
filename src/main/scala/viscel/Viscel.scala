package viscel

import java.nio.file.{Path, Paths}

import better.files.File
import cats.implicits._
import com.monovore.decline.{Command, Opts}
import viscel.shared.Log

object Viscel {

  val port       : Opts[Int]     = Opts.option[Int](long = "port",
                                                    help = "Weberver listening port.")
                                   .withDefault(2358)
  val interface  : Opts[String]  = Opts.option[String](long = "interface", metavar = "interface",
                                                       help = "Interface to bind the server on.")
                                   .withDefault("0")
  val server     : Opts[Boolean] = Opts.flag(long = "noserver",
                                             help = "Do not start the server.").orTrue
  val core       : Opts[Boolean] = Opts.flag(long = "nodownload",
                                             help = "Do not start the downloader.").orTrue
  val optBasedir : Opts[Path]    = Opts.option[Path](long = "basedir", metavar = "directory",
                                                     help = "Base directory to store settings and data.")
                                   .withDefault(Paths.get("./data/"))
  val optStatic  : Opts[Path]    = Opts.option[Path](long = "static", metavar = "directory",
                                                     help = "Directory of static resources.")
                                   .withDefault(Paths.get("./static/"))
  val shutdown   : Opts[Boolean] = Opts.flag(long = "shutdown",
                                             help = "Shutdown directly.")
                                   .orFalse
  val cleanblobs : Opts[Boolean] = Opts.flag(long = "cleanblobs",
                                             help = "Cleans blobs from blobstore which are no longer linked.")
                                   .orFalse
  val optBlobdir : Opts[Path]    = Opts.option[Path](long = "blobdir", metavar = "directory",
                                                     help = "Directory to store blobs (the images). Can be absolute, otherwise relative to basedir.")
                                   .withDefault(Paths.get("./blobs/"))
  val optPostsdir: Opts[Path]    = Opts.option[Path](long = "postsdir", metavar = "directory",
                                                     help = "Directory to store posts. Can be absolute, otherwise relative to basedir.")
                                   .withDefault(Paths.get("./posts/"))

  val command: Command[Services] = Command(name = "viscel", header = "Start viscel!") {
    (optBasedir, port, interface, server, core, cleanblobs, optBlobdir, shutdown, optStatic).mapN {
      (optBasedir, port, interface, server, core, cleanblobs, optBlobdir, shutdown, optStatic) =>

        val staticCandidates = List(File(optBasedir.resolve(optStatic)),
                                    File(optStatic))

        val staticDir =
          staticCandidates.find(_.isDirectory)
          .getOrElse {
            println("Missing static resource directory.")
            println(s"Searched in ${staticCandidates.mkString(" ")}")
            sys.exit(0)
          }

        val services = new Services(optBasedir, optBlobdir, staticDir.path, interface, port)

        if (cleanblobs) {
          services.replUtil.cleanBlobDirectory()
        }

        if (server) {
          services.startServer()
        }

        if (core) {
          services.clockwork.recheckPeriodically()
          services.activateNarrationHint()
        }

        if (shutdown) {
          services.terminateServer()
        }
        Log.Main.info("initialization done")
        services
    }
  }

  val version: String = better.files.Resource.my.asString("/version").getOrElse("unknown")

  def main(args: Array[String]): Unit = run(args: _*)

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
