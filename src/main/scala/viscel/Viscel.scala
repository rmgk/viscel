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
import rescala._
import viscel.crawl.{Clockwork, RequestUtil}
import viscel.narration.Narrator
import viscel.scribe.Scribe
import viscel.server.Server
import viscel.shared.Log
import viscel.store.{BlobStore, DescriptionCache, Users}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions

object Viscel {

	var metarratorconfigdir: Path = _
	var definitionsdir: Path = _
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

		val basepath = Paths.get(optBasedir())
		val blobdir = basepath.resolve(optBlobdir())
		val scribedir = basepath.resolve("db3")
		val cachedir = basepath.resolve("cache")
		metarratorconfigdir = basepath.resolve("metarrators")
		definitionsdir = basepath.resolve("definitions")
		exportdir = basepath.resolve("export")
		val usersdir = basepath.resolve("users")
		Files.createDirectories(cachedir)

		if (upgrade.?) {
			Log.warn(s"upgrade not supported in this version. try `v7.0.0-NeoUpgrade`")
		}

		Files.createDirectories(scribedir)

		val desciptionCache = new DescriptionCache(cachedir)

		val scribe = new viscel.scribe.Scribe(scribedir, desciptionCache)
		val blobs = new BlobStore(blobdir)

		if (cleanblobs.?) {
			Log.info(s"scanning all blobs …")
			val blobsHashesInDB = scribe.allBlobsHashes()
			Log.info(s"scanning files …")
			val bsn = new BlobStore(basepath.resolve("blobbackup"))

			val seen = mutable.HashSet[String]()

			Files.walk(blobdir).iterator().asScala.filter(Files.isRegularFile(_)).foreach { bp =>
				val sha1path = s"${bp.getName(bp.getNameCount - 2)}${bp.getFileName}"
				//val sha1 = blobs.sha1hex(Files.readAllBytes(bp))
				//if (sha1path != sha1) Log.warn(s"$sha1path did not match")
				seen.add(sha1path)
				if (!blobsHashesInDB.contains(sha1path)) {
					val newpath = bsn.hashToPath(sha1path)
					Log.info(s"moving $bp to $newpath")
					Files.createDirectories(newpath.getParent)
					Files.move(bp, newpath)
				}
			}
			blobsHashesInDB.diff(seen).foreach(sha1 => Log.info(s"$sha1 is missing"))
		}

		val system = ActorSystem()
		val materializer = ActorMaterializer()(system)
		val http: HttpExt = Http(system)

		val executionContext = ExecutionContext.fromExecutor(new ThreadPoolExecutor(
			0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]))

		val requests = new RequestUtil(blobs, http)(executionContext, materializer)

		var narrationHint: Evt[(Narrator, Boolean)] = Evt()

		val userStore = new Users(usersdir)

		if (!noserver.?) {

			val boundServer = Promise[ServerBinding]()

			def terminate(): Unit = {
				boundServer.future
					.flatMap(_.unbind())(system.dispatcher)
					.onComplete { _ =>
						system.terminate()
					}(system.dispatcher)
			}

			val server = new Server(userStore, scribe, blobs, requests, () => terminate())(system)
			val boundSocket: Future[ServerBinding] = http.bindAndHandle(
				RouteResult.route2HandlerFlow(server.route)(
					RoutingSettings.default(system),
					ParserSettings.default(system),
					materializer,
					RoutingLog.fromActorSystem(system)),
				"0", port())(materializer)

			narrationHint = server.narratorHint

			boundServer.completeWith(boundSocket)
		}

		if (!nocore.?) {
			val cw = new Clockwork(cachedir.resolve("crawl-times.json"), scribe, executionContext, requests, userStore)

			narrationHint.observe { case (narrator, force) =>
				desciptionCache.invalidate(narrator.id)
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
		val optBasedir = accepts("basedir", "the base working directory")
			.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo("./data/").describedAs("basedir")
		val nodbwarmup = accepts("nodbwarmup", "skip database warmup")
		val shutdown = accepts("shutdown", "shutdown after main")
		val help = accepts("help").forHelp()
		val upgrade = accepts("upgradedb", "upgrade database and folder layout from version 2 to version 3")
		val cleanblobs = accepts("cleanblobs", "cleans blobs from blobstore which are no longer linked")
		val optBlobdir = accepts("blobdir", "directory to store blobs (the images). Can be absolute, otherwise relative to basedir")
			.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo("./blobs/").describedAs("blobdir")

		implicit def optToBool(opt: OptionSpecBuilder)(implicit oset: OptionSet): Boolean = oset.has(opt)

		implicit class OptEnhancer[T](opt: OptionSpec[T]) {
			def ?(implicit oset: OptionSet): Boolean = oset.has(opt)
			def get(implicit oset: OptionSet): Option[T] = if (!oset.has(opt)) None else Some(apply())
			def apply()(implicit oset: OptionSet): T = oset.valueOf(opt)
		}

	}


}

