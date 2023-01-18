package viscel

import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path, Paths}
import java.io.File as jFile
import viscel.shared.Log
import viscel.store.BlobStore

import scala.collection.immutable.ArraySeq
import com.softwaremill.quicklens.{modifyLens as path, *}

object Viscel {

  extension [A, C](inline oparse: scopt.OParser[A, C]) {
    inline def lens(inline path: PathLazyModify[C, A]) = oparse.mlens(path, identity)
    inline def mlens[B](inline path: PathLazyModify[C, B], map: A => B) =
      oparse.action((a, c) => (path.setTo(map(a))(c)))
    inline def vlens[B](inline path: PathLazyModify[C, B], value: B) =
      oparse.action((a, c) => (path.setTo(value)(c)))
  }

  case class Args(
      optBasedir: Path = Paths.get("./data"),
      port: Int = 2358,
      interface: String = "0",
      server: Boolean = true,
      core: Boolean = true,
      cleanblobs: Boolean = false,
      optBlobdir: Path = Paths.get("blobs"),
      shutdown: Boolean = false,
      optStatic: Path = Paths.get("static"),
      urlPrefix: String = "",
      collectDbGarbage: Boolean = false,
  )

  val soptags = {
    val builder = scopt.OParser.builder[Args]
    import builder.*
    scopt.OParser.sequence(
      programName("viscel"),
      head("Start viscel!"),
      help('h', "help").hidden(),
      opt[jFile]("basedir").valueName("directory").text("Base directory to store settings and data.")
        .mlens(path(_.optBasedir), _.toPath),
      opt[Int]("port").text("Weberver listening port.").lens(path(_.port)),
      opt[String]("interface").valueName("interface").text("Interface to bind the server to.").lens(path(_.interface)),
      opt[Unit]("noserver").text("Do not start the server.").vlens(path(_.server), false),
      opt[Unit]("nodownload").text("Do not start the downloader.").vlens(path(_.core), false),
      opt[Unit]("cleanblobs").text("Cleans blobs from blobstore which are no longer linked.")
        .vlens(path(_.cleanblobs), true),
      opt[jFile]("blobdir").valueName("directory")
        .text("Directory to store blobs (the images). Can be absolute, otherwise relative to basedir.")
        .mlens(path(_.optBlobdir), _.toPath),
      opt[Unit]("shutdown").text("Shutdown directly.").vlens(path(_.shutdown), true),
      opt[jFile]("static").valueName("directory").text("Directory of static resources.")
        .mlens(path(_.optStatic), _.toPath),
      opt[String]("urlprefix").text("Prefix for server URLs.").lens(path(_.urlPrefix)),
      opt[Unit]("collectgarbage").text("Finds unused parts in the database.").vlens(path(_.collectDbGarbage), true)
    )
  }

  def makeService(args: Args): Services = {
    import args.*
    val staticCandidates = List(optBasedir.resolve(optStatic), optStatic)

    val staticDir =
      staticCandidates.find(x => Files.isDirectory(x))
        .getOrElse {
          println(s"Missing optStatic resource directory, " +
            s"tried ${staticCandidates.map(c => s"»$c«").mkString(", ")}.")
          sys.exit(0)
        }

    val services = new Services(optBasedir, optBlobdir, staticDir, urlPrefix, interface, port)

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

  val version: String = viscel.shared.BuildInfo.version

  def main(args: Array[String]): Unit = {
    run(ArraySeq.unsafeWrapArray(args): _*)
    ()
  }

  def run(args: String*): Option[Services] = {
    Log.Main.info(s"Viscel version $version")
    scopt.OParser.parse(soptags, args, Args()).map(makeService)
  }
}
