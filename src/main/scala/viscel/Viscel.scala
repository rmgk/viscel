package viscel

import java.nio.file.{Path, Paths}

import cats.implicits._
import com.monovore.decline.{Opts, Command}
import viscel.shared.Log

object Viscel {

  val port = Opts.option[Int]("port", help = "Weberver listening port.").withDefault(2358)
  val interface = Opts.option[String]("interface", metavar = "interface", help = "Interface to bind the server on.").withDefault("0")
  val noserver = Opts.flag("noserver", "Do not start the server.").orFalse
  val nocore = Opts.flag("nodownload", "Do not start the downloader.").orFalse
  val optBasedir = Opts.option[Path]("basedir", short = "b", metavar = "directory",
    help = "Base directory to store settings and data.").withDefault(Paths.get("./data/"))
  val shutdown = Opts.flag("shutdown", "Shutdown directly.").orFalse
  val cleanblobs = Opts.flag("cleanblobs", "Cleans blobs from blobstore which are no longer linked.").orFalse
  val optBlobdir = Opts.option[Path]("blobdir", metavar = "directory",
    help = "Directory to store blobs (the images). Can be absolute, otherwise relative to basedir.").withDefault(Paths.get("./blobs/"))
  val optPostsdir = Opts.option[Path]("postsdir", metavar = "directory",
    help = "Directory to store posts. Can be absolute, otherwise relative to basedir.").withDefault(Paths.get("./posts/"))

  val command = Command(name = "viscel", header = "Start viscel!") {
    (optBasedir, port, interface, noserver, nocore, cleanblobs, optBlobdir, shutdown, optPostsdir).mapN {
      (optBasedir, port, interface, noserver, nocore, cleanblobs, optBlobdir, shutdown, optPostsdir) =>
        val services = new Services(optBasedir, optBlobdir, optPostsdir, interface, port)

        if (cleanblobs) {
          services.replUtil.cleanBlobDirectory()
        }

        if (!noserver) {
          services.startServer()
        }

        if (!nocore) {
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


  def main(args: Array[String]): Unit = run(args: _*)

  def run(args: String*): Services = {
    command.parse(args) match {
      case Left(help) =>
        println(help)
        sys.exit(0)
      case Right(service) => service
    }
  }
}
