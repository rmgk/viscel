package viscel

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{RouteResult, RoutingLog}
import akka.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import rescala.Evt
import viscel.crawl.{Clockwork, RequestUtil}
import viscel.narration.Narrator
import viscel.server.{Server, ServerPages}
import viscel.store.{BlobStore, DescriptionCache, NarratorCache, Users}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class Services(basedirString: String, blobdirString: String, interface: String, port: Int) {


	/* ====== paths ====== */

	def create(p: Path): Path = {
		Files.createDirectories(p)
		p
	}
	val basepath: Path = Paths.get(basedirString)
	val blobdir: Path = basepath.resolve(blobdirString)
	lazy val scribedir: Path = create(basepath.resolve("db3"))
	lazy val cachedir: Path = create(basepath.resolve("cache"))
	val metarratorconfigdir: Path = basepath.resolve("metarrators")
	val definitionsdir: Path = basepath.resolve("definitions")
	val exportdir: Path = basepath.resolve("export")
	val usersdir: Path = basepath.resolve("users")


	/* ====== storage ====== */

	lazy val desciptionCache = new DescriptionCache(cachedir)
	lazy val blobs = new BlobStore(blobdir)
	lazy val userStore = new Users(usersdir)
	lazy val scribe = new viscel.scribe.Scribe(scribedir, desciptionCache)
	lazy val narratorCache: NarratorCache = new NarratorCache(metarratorconfigdir)


	/* ====== execution ====== */

	lazy val system = ActorSystem()
	lazy val materializer: ActorMaterializer = ActorMaterializer()(system)
	lazy val http: HttpExt = Http(system)
	lazy val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(new ThreadPoolExecutor(
		0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]))

	/* ====== http requests ====== */

	lazy val requests = new RequestUtil(blobs, http)(executionContext, materializer)


	/* ====== repl util extra tasks ====== */

	lazy val replUtil = new ReplUtil(this)


	/* ====== main webserver ====== */

	lazy val serverPages = new ServerPages(scribe, narratorCache)
	lazy val server = new Server(userStore, scribe, blobs, requests,
		() => terminateServer(), narrationHint, serverPages, replUtil, system, narratorCache)
	lazy val serverBinding: Future[ServerBinding] = http.bindAndHandle(
		RouteResult.route2HandlerFlow(server.route)(
			RoutingSettings.default(system),
			ParserSettings.default(system),
			materializer,
			RoutingLog.fromActorSystem(system)),
		interface, port)(materializer)

	def startServer(): Future[ServerBinding] = serverBinding
	def terminateServer(): Unit = {
		serverBinding
			.flatMap(_.unbind())(system.dispatcher)
			.onComplete { _ =>
				system.terminate()
			}(system.dispatcher)
	}


	/* ====== clockwork ====== */

	lazy val clockwork: Clockwork = new Clockwork(cachedir.resolve("crawl-times.json"),
		scribe, executionContext, requests, userStore, narratorCache)

	def activateNarrationHint() = {
		narrationHint.observe { case (narrator, force) =>
			desciptionCache.invalidate(narrator.id)
			clockwork.runNarrator(narrator, if (force) 0 else clockwork.dayInMillis * 1)
		}
	}


	/* ====== notifications ====== */

	lazy val narrationHint: Evt[(Narrator, Boolean)] = Evt[(Narrator, Boolean)]()


}
