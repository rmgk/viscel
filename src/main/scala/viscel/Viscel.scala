package viscel

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl._
import akka.http.scaladsl.server.{RouteResult, _}
import akka.http.scaladsl.settings._
import akka.stream.ActorMaterializer
import joptsimple.{BuiltinHelpFormatter, OptionException, OptionParser, OptionSet, OptionSpec, OptionSpecBuilder}
import viscel.crawl.{Clockwork, RequestUtil}
import viscel.scribe.ScribePicklers._
import viscel.scribe.Scribe
import viscel.server.Server
import viscel.shared.Log
import viscel.store.BlobStore

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions

object Viscel {


	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		Console.println(s"$desc took ${(System.nanoTime - start) / 1000000.0} ms")
		res
	}

	var basepath: Path = _
	var metarratorconfigdir: Path = _
	var definitionsdir: Path = _
	var usersdir: Path = _
	var exportdir: Path = _



	def main(args: Array[String]): Unit = run(args: _*)

	def run(args: String*): (ActorSystem, Scribe) = {
		import viscel.Viscel.Options._
		val formatWidth = try {new jline.console.ConsoleReader().getTerminal.getWidth}
		catch {case e: Throwable => 80}
		formatHelpWith(new BuiltinHelpFormatter(formatWidth, 4))
		implicit val conf = try {
			parse(args: _*)
		}
		catch {
			case oe: OptionException =>
				printHelpOn(System.out)
				Console.println()
				Console.println(s"$oe")
				sys.exit(0)
		}

		if (help.? || conf.nonOptionArguments.size > 0) {
			printHelpOn(System.out)
			sys.exit(0)
		}

		basepath = Paths.get(basedir())
		val db2dir = basepath.resolve("scribe/db2")
		val oldBlobdir = basepath.resolve("scribe/blobs")
		val blobdir = basepath.resolve("blobs")
		val scribedir = basepath.resolve("db3")
		val cachedir = basepath.resolve("cache")
		val configdir = basepath.resolve("config")
		metarratorconfigdir = basepath.resolve("metarrators")
		definitionsdir = basepath.resolve("definitions")
		exportdir = basepath.resolve("export")
		usersdir = basepath.resolve("users")
		Files.createDirectories(cachedir)

		if (upgrade.?) {
			if (Files.isDirectory(db2dir) ) {
				if (Files.exists(scribedir)) {
					Log.warn(s"can not convert database, target exists: $scribedir")
				}
				else {
					Files.createDirectories(scribedir)
					Log.info("converting database …")
					viscel.neoadapter.NeoAdapter.convertToAppendLog(db2dir, scribedir, configdir)
				}
			}

			if (Files.isDirectory(oldBlobdir) ) {
				if (Files.exists(blobdir)) { Log.warn(s"can not move blobs, target exists: $blobdir") }
				else {
					Log.info("moving blobs …")
					Files.move(oldBlobdir, blobdir)
				}
			}

			Files.move(basepath.resolve("data/updateTimes.json"), cachedir.resolve("crawl-times.json"))
			Files.move(basepath.resolve("data"), metarratorconfigdir)

		}

		Files.createDirectories(scribedir)

		val scribe = new viscel.scribe.Scribe(scribedir, cachedir)
		val blobs = new BlobStore(blobdir)

		val system = ActorSystem()
		val materializer = ActorMaterializer()(system)
		val http: HttpExt = Http(system)

		val executionContext = ExecutionContext.fromExecutor(new ThreadPoolExecutor(
			0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]))

		val requests = new RequestUtil(blobs, http)(executionContext, materializer)

		if (!noserver.?) {

			val boundServer = Promise[ServerBinding]()

			def terminate(): Unit = {
				boundServer.future
					.flatMap(_.unbind())(system.dispatcher)
					.onComplete { _ =>
						system.terminate()
					}(system.dispatcher)
			}

			val server = new Server(scribe, blobs, requests, terminate)(system)
			val boundSocket: Future[ServerBinding] = http.bindAndHandle(
				RouteResult.route2HandlerFlow(server.route)(
					RoutingSettings.default(system),
					ParserSettings.default(system),
					materializer,
					RoutingLog.fromActorSystem(system)),
				"0", port())(materializer)

			boundServer.completeWith(boundSocket)
		}

		if (!nocore.?) {
			val cw = new Clockwork(cachedir.resolve("crawl-times.json"), scribe, executionContext, requests)

			Deeds.narratorHint = (narrator, force) => {
				cw.runNarrator(narrator, if (force) 0 else cw.dayInMillis * 1)
			}
			cw.recheckPeriodically()


		}

		if (shutdown.?) {
			system.terminate()
		}

		Log.info("initialization done")

		(system, scribe)
	}


	object Options extends OptionParser {
		//	val loglevel = accepts("loglevel", "set the loglevel")
		//		.withRequiredArg().describedAs("loglevel").defaultsTo("INFO")
		val port = accepts("port", "server listening port")
			.withRequiredArg().ofType(Predef.classOf[Int]).defaultsTo(2358).describedAs("port")
		val noserver = accepts("noserver", "do not start the server")
		val nocore = accepts("nocore", "do not start the core downloader")
		val basedir = accepts("basedir", "die base working directory")
			.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo("./data/").describedAs("basedir")
		val nodbwarmup = accepts("nodbwarmup", "skip database warmup")
		val shutdown = accepts("shutdown", "shutdown after main")
		val help = accepts("help").forHelp()
		val upgrade = accepts("upgradedb", "upgrade database and folder layout from version 2 to version 3")

		implicit def optToBool(opt: OptionSpecBuilder)(implicit oset: OptionSet): Boolean = oset.has(opt)

		implicit class OptEnhancer[T](opt: OptionSpec[T]) {
			def ?(implicit oset: OptionSet): Boolean = oset.has(opt)
			def get(implicit oset: OptionSet): Option[T] = if (!oset.has(opt)) None else Some(apply())
			def apply()(implicit oset: OptionSet): T = oset.valueOf(opt)
		}

	}


}

