package vitzen

import java.nio.file.{Path, Paths}

import cats.implicits._
import com.monovore.decline.{Command, Opts}
import vitzen.logging.Level

object Vitzen {

  val Log = logging.Logger(level = Level.Trace)

  val port = Opts.option[Int]("port", help = "Weberver listening port.").withDefault(2358)
  val interface = Opts.option[String]("interface", metavar = "interface", help = "Interface to bind the server on.").withDefault("0")
  val noserver = Opts.flag("noserver", "Do not start the server.").orFalse
  val nocore = Opts.flag("nodownload", "Do not start the downloader.").orFalse
  val optBasedir = Opts.option[Path]("basedir", short = "b", metavar = "directory",
    help = "Base directory to store settings and data.").withDefault(Paths.get("./data/"))
  val shutdown = Opts.flag("shutdown", "Shutdown directly.").orFalse
  val optContent = Opts.option[Path]("contentdir", metavar = "directory",
    help = "Directory to store content. Can be absolute, otherwise relative to basedir.").withDefault(Paths.get("./content/"))

  val command = Command(name = "viscel", header = "Start viscel!") {
    (optBasedir, port, interface, noserver, nocore, optContent, shutdown).mapN {
      (optBasedir, port, interface, noserver, nocore, optDatadir, shutdown) =>
        val services = new Services(Log, optBasedir, optDatadir, interface, port)


        if (!noserver) {
          services.startServer()
        }


        if (shutdown) {
          services.terminateServer()
        }
        Log.info("initialization done")
        services
    }
  }


  def main(args: Array[String]): Unit = run(args: _*)

  def run(args: String*): Services = {
    Log.debug("initializing")
    command.parse(args) match {
      case Left(help) =>
        println(help)
        sys.exit(0)
      case Right(service) => service
    }
  }
}
