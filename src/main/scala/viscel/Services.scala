package viscel

import java.nio.file.{Files, Path}
import java.util.TimerTask
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{RouteResult, RoutingLog}
import akka.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory.parseString
import rescala.default.{Evt, implicitScheduler}
import viscel.crawl.{CrawlScheduler, CrawlServices}
import viscel.narration.Narrator
import viscel.netzi.OkHttpRequester
import viscel.server.{ContentLoader, Interactions, Server, ServerPages}
import viscel.shared.Log
import viscel.store.{BlobStore, DescriptionCache, NarratorCache, StoreManager, Users}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class Services(relativeBasedir: Path,
               relativeBlobdir: Path,
               val staticDir: Path,
               val interface: String,
               val port: Int) {


  /* ====== paths ====== */

  def create(p: Path): Path = {
    Files.createDirectories(p)
    p
  }
  val basepath           : Path = relativeBasedir.toAbsolutePath
  val blobdir            : Path = basepath.resolve(relativeBlobdir)
  val metarratorconfigdir: Path = basepath.resolve("metarrators")
  val definitionsdir     : Path = staticDir
  val exportdir          : Path = basepath.resolve("export")
  val usersdir           : Path = basepath.resolve("users")
  val db3dir             : Path = basepath.resolve("db3")
  lazy val db4dir  : Path = create(basepath.resolve("db4"))
  lazy val cachedir: Path = create(basepath.resolve("cache"))


  /* ====== storage ====== */

  lazy val descriptionCache = new DescriptionCache(cachedir)
  lazy val blobStore        = new BlobStore(blobdir)
  lazy val userStore        = new Users(usersdir)
  lazy val rowStore         = new StoreManager(db3dir, db4dir).transition()
  lazy val narratorCache    = new NarratorCache(metarratorconfigdir, definitionsdir)
  lazy val folderImporter   = new FolderImporter(blobStore, rowStore, descriptionCache)


  /* ====== akka ====== */

  val actorConfig = """
akka.http {
	client {
		user-agent-header = viscel/7
		connecting-timeout = 30s
		response-chunk-aggregation-limit = 32m
		chunkless-streaming = off
	}
	host-connection-pool {
		max-connections = 1
		pipelining-limit = 1
		max-retries = 3
	}
	parsing {
		max-content-length = 32m
		max-chunk-size = 32m
		max-to-strict-bytes = 32m
	}
}
akka {
	log-dead-letters = 0
	log-dead-letters-during-shutdown = off
	log-config-on-start = off
}
"""

  def executorMinMax(min: Int = 0, max: Int = 1, keepAliveSeconds: Long = 1L) = {
    new ThreadPoolExecutor(min, max, keepAliveSeconds,
                           TimeUnit.SECONDS,
                           new LinkedBlockingQueue[Runnable]())
  }


  lazy val akkaExecutionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(executorMinMax(max = 1))


  lazy val system: ActorSystem = ActorSystem(name = "viscel",
                                             config = Some(parseString(actorConfig)),
                                             defaultExecutionContext = Some(akkaExecutionContext))

  lazy val materializer: ActorMaterializer = ActorMaterializer()(system)
  lazy val http        : HttpExt           = Http(system)


  /* ====== http requests ====== */


  lazy val requests = {
    val maxRequests             = 5
    val requestExecutionContext = executorMinMax(max = maxRequests)
    new OkHttpRequester(maxRequests, requestExecutionContext)
  }

  /* ====== repl util extra tasks ====== */

  lazy val replUtil = new ReplUtil(this)


  /* ====== main webserver ====== */

  lazy val contentLoader = new ContentLoader(narratorCache, rowStore, descriptionCache)
  lazy val serverPages   = new ServerPages()
  lazy val interactions  = new Interactions(contentLoader = contentLoader,
                                            narratorCache = narratorCache,
                                            narrationHint = narrationHint,
                                            userStore = userStore,
                                            requestUtil = requests)
  lazy val server        = new Server(userStore = userStore,
                                      blobStore = blobStore,
                                      terminate = () => terminateServer(),
                                      pages = serverPages,
                                      folderImporter = folderImporter,
                                      interactions = interactions,
                                      staticPath = staticDir
                                      )

  lazy val serverBinding: Future[ServerBinding] = http.bindAndHandle(
    RouteResult.route2HandlerFlow(server.route)(
      RoutingSettings.default(system),
      ParserSettings.default(system),
      materializer,
      RoutingLog.fromActorSystem(system)),
    interface, port)(materializer)

  def startServer(): Future[ServerBinding] = serverBinding

  /** Termination works by firs stopping the server nicely
   * and then killing the actor system, which in turn stops
   * crawler from downloading, shutting down the whole application */
  def terminateServer(): Unit = {
    new java.util.Timer(true).schedule(new TimerTask {
      override def run(): Unit = serverBinding
        .flatMap(_.unbind())(akkaExecutionContext)
        .onComplete { _ =>
          system.terminate()
          Log.Main.info("shutdown")
        }(akkaExecutionContext)
    }, 1000)

  }


  /* ====== clockwork ====== */

  lazy val computeExecutionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(executorMinMax(max = 1))

  lazy val crawl: CrawlServices = new CrawlServices(blobStore = blobStore,
                                                    requestUtil = requests,
                                                    rowStore = rowStore,
                                                    descriptionCache = descriptionCache,
                                                    executionContext = computeExecutionContext)

  lazy val clockwork: CrawlScheduler = new CrawlScheduler(path = cachedir.resolve("crawl-times.json"),
                                                          crawlServices = crawl,
                                                          ec = computeExecutionContext,
                                                          userStore = userStore,
                                                          narratorCache = narratorCache)

  def activateNarrationHint() = {
    narrationHint.observe { case (narrator, force) =>
      if (force) narratorCache.updateCache()
      descriptionCache.invalidate(narrator.id)
      clockwork.runNarrator(narrator, if (force) 0 else clockwork.dayInMillis * 1)
    }
  }


  /* ====== notifications ====== */

  lazy val narrationHint: Evt[(Narrator, Boolean)] = Evt[(Narrator, Boolean)]()


}
