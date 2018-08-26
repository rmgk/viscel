package viscel

import java.nio.file.{Files, Path}
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{RouteResult, RoutingLog}
import akka.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import rescala.default.{Evt, implicitScheduler}
import viscel.crawl.{AkkaHttpRequester, Clockwork, Crawl}
import viscel.narration.Narrator
import viscel.server.{ContentLoader, Server, ServerPages}
import viscel.store.{BlobStore, DescriptionCache, NarratorCache, RowStore, Users}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class Services(relativeBasedir: Path, relativeBlobdir: Path, relativePostdir: Path, interface: String, port: Int) {


  /* ====== paths ====== */

  def create(p: Path): Path = {
    Files.createDirectories(p)
    p
  }
  val basepath           : Path = relativeBasedir.toAbsolutePath
  val blobdir            : Path = basepath.resolve(relativeBlobdir)
  val metarratorconfigdir: Path = basepath.resolve("metarrators")
  val definitionsdir     : Path = basepath.resolve("definitions")
  val exportdir          : Path = basepath.resolve("export")
  val usersdir           : Path = basepath.resolve("users")
  val postsdir           : Path = basepath.resolve(relativePostdir)
  lazy val scribedir: Path = create(basepath.resolve("db3"))
  lazy val cachedir : Path = create(basepath.resolve("cache"))


  /* ====== storage ====== */

  lazy val descriptionCache = new DescriptionCache(cachedir)
  lazy val blobs            = new BlobStore(blobdir)
  lazy val userStore        = new Users(usersdir)
  lazy val rowStore         = new RowStore(scribedir)
  lazy val narratorCache    = new NarratorCache(metarratorconfigdir, definitionsdir)


  /* ====== execution ====== */

  lazy val system                                     = ActorSystem()
  lazy val materializer    : ActorMaterializer        = ActorMaterializer()(system)
  lazy val http            : HttpExt                  = Http(system)
  lazy val executionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(new ThreadPoolExecutor(0, 1, 1L,
                                                         TimeUnit.SECONDS,
                                                         new LinkedBlockingQueue[Runnable]))

  /* ====== http requests ====== */

  lazy val requests = new AkkaHttpRequester(http)(executionContext, materializer)

  /* ====== repl util extra tasks ====== */

  lazy val replUtil = new ReplUtil(this)


  /* ====== main webserver ====== */

  lazy val contentLoader = new ContentLoader(narratorCache, rowStore, descriptionCache)
  lazy val serverPages = new ServerPages()
  lazy val server      = new Server(userStore = userStore,
                                    contentLoader = contentLoader,
                                    blobStore = blobs,
                                    requestUtil = requests,
                                    terminate = () => terminateServer(),
                                    narratorHint = narrationHint,
                                    pages = serverPages,
                                    replUtil = replUtil,
                                    system = system,
                                    narratorCache = narratorCache,
                                    postsPath = postsdir
  )

  lazy val serverBinding: Future[ServerBinding] = http.bindAndHandle(
    RouteResult.route2HandlerFlow(server.route)(
      RoutingSettings
      .default(system),
      ParserSettings
      .default(system),
      materializer,
      RoutingLog
      .fromActorSystem(system)),
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

  lazy val crawl: Crawl = new Crawl(blobStore = blobs,
                                    requestUtil = requests,
                                    rowStore = rowStore,
                                    descriptionCache = descriptionCache)(executionContext)

  lazy val clockwork: Clockwork = new Clockwork(path = cachedir.resolve("crawl-times.json"),
                                                crawl = crawl,
                                                ec = executionContext,
                                                userStore = userStore,
                                                narratorCache = narratorCache)

  def activateNarrationHint() = {
    narrationHint.observe { case (narrator, force) =>
      descriptionCache.invalidate(narrator.id)
      clockwork.runNarrator(narrator, if (force) 0 else clockwork.dayInMillis * 1)
    }
  }


  /* ====== notifications ====== */

  lazy val narrationHint: Evt[(Narrator, Boolean)] = Evt[(Narrator, Boolean)]()


}
