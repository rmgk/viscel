package viscel

import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path, Paths}
import viscel.shared.Log
import viscel.store.BlobStore

import scala.collection.immutable.ArraySeq
import de.rmgk.options.*
import scopt.OParser

object Viscel {

  type Arg[T] = Argument[T, Single, Style.Named]
  type BFlag  = Argument[Boolean, Flag, Style.Named]

  case class Args(
      basedir: Arg[Path] = Argument(
        _.valueName("directory").text("Base directory to store settings and data."),
        Some(Paths.get("./data"))
      ),
      port: Arg[Int] = Argument(_.text("Weberver listening port."), Some(2358)),
      interface: Arg[String] = Argument(_.valueName("interface").text("Interface to bind the server to."), Some("0")),
      noserver: BFlag = Argument(_.text("Do not start the server."), Some(false)),
      nodownload: BFlag = Argument(_.text("Do not start the downloader."), Some(false)),
      cleanblobs: BFlag = Argument(_.text("Cleans blobs from blobstore which are no longer linked."), Some(false)),
      blobdir: Arg[Path] = Argument(
        _.valueName("directory")
          .text("Directory to store blobs (the images). Can be absolute, otherwise relative to basedir."),
        Some(Paths.get("blobs"))
      ),
      shutdown: BFlag = Argument(_.text("Shutdown directly."), Some(false)),
      static: Arg[Path] =
        Argument(_.valueName("directory").text("Directory of static resources."), Some(Paths.get("static"))),
      urlprefix: Arg[String] = Argument(_.text("Prefix for server URLs."), Some("")),
      collectgarbage: BFlag = Argument(_.text("Finds unused parts in the database."), Some(false)),
  )

  def makeService(args: Args): Services = {
    import args.*
    val staticCandidates = List(basedir.value.resolve(static.value), static.value)

    val staticDir =
      staticCandidates.find(x => Files.isDirectory(x))
        .getOrElse {
          println(s"Missing optStatic resource directory, " +
            s"tried ${staticCandidates.map(c => s"»$c«").mkString(", ")}.")
          sys.exit(0)
        }

    val services = new Services(basedir.value, blobdir.value, staticDir, urlprefix.value, interface.value, port.value)

    if (cleanblobs.value) {
      BlobStore.cleanBlobDirectory(services)
    }

    if (collectgarbage.value) {
      services.rowStore.computeGarbage()
    }

    if (!noserver.value) {
      Log.Main.info(s"starting server")
      services.startServer()
    }

    if (!nodownload.value) {
      services.clockwork.recheckPeriodically()
      services.activateNarrationHint()
      ()
    }

    if (shutdown.value) {
      services.terminateEverything(!noserver.value)
    }
    Log.Main.info(s"initialization done in ${ManagementFactory.getRuntimeMXBean.getUptime}ms")
    services
  }

  val version: String = viscel.shared.BuildInfo.version

  def main(args: Array[String]): Unit = {
    run(ArraySeq.unsafeWrapArray(args): _*)
    ()
  }

  def run(args: String*): Option[Services] = {
    Log.Main.info(s"Viscel version $version")
    parseCli(
      Args(),
      args,
      { builder =>
        import builder.*
        OParser.sequence(
          programName("viscel"),
          head("Start viscel!"),
          help('h', "help").hidden()
        )

      }
    ) match
      case None => None
      case Some(cliArgs) => Some(makeService(cliArgs))
  }
}
