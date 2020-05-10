package viscel

import java.nio.file.{Files, Path}
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import rescala.default.{Evt, implicitScheduler}
import viscel.crawl.{CrawlScheduler, CrawlServices}
import viscel.narration.Narrator
import viscel.netzi.OkHttpRequester
import viscel.server.{ContentLoader, Interactions, JavalinServer, ServerPages}
import viscel.store.{BlobStore, DescriptionCache, NarratorCache, StoreManager, Users}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

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



  /* ====== executors ====== */

  def executorMinMax(min: Int = 0, max: Int = 1, keepAliveSeconds: Long = 1L) = {
    new ThreadPoolExecutor(min, max, keepAliveSeconds,
                           TimeUnit.SECONDS,
                           new LinkedBlockingQueue[Runnable]())
  }

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

  lazy val server: JavalinServer = new JavalinServer(blobStore = blobStore,
                                      terminate = () => terminateServer(),
                                      pages = serverPages,
                                      folderImporter = folderImporter,
                                      interactions = interactions,
                                      staticPath = staticDir,
                                      urlPrefix = ""
                                                     )

  def startServer() = server.start(interface, port)
  def terminateServer() = server.stop()


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
