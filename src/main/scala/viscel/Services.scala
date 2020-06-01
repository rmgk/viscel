package viscel

import java.nio.file.{Files, Path}
import java.util.TimerTask
import java.util.concurrent.{LinkedBlockingQueue, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import rescala.default.{Evt, implicitScheduler}
import viscel.crawl.{CrawlScheduler, CrawlServices}
import viscel.narration.Narrator
import viscel.netzi.OkHttpRequester
import viscel.server.{ContentLoader, Interactions, JavalinServer, ServerPages}
import viscel.shared.Log
import viscel.store.{BlobStore, DescriptionCache, NarratorCache, RowStoreV4, Users}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.control.NonFatal

class Services(relativeBasedir: Path,
               relativeBlobdir: Path,
               val staticDir: Path,
               val urlPrefix: String,
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
  lazy val db4dir  : Path = create(basepath.resolve("db4"))
  lazy val cachedir: Path = create(basepath.resolve("cache"))


  /* ====== storage ====== */

  lazy val descriptionCache = new DescriptionCache(cachedir)
  lazy val blobStore        = new BlobStore(blobdir)
  lazy val userStore        = new Users(usersdir, contentLoader)
  lazy val rowStore         = new RowStoreV4(db4dir)
  lazy val narratorCache    = new NarratorCache(metarratorconfigdir, definitionsdir)
  lazy val folderImporter   = new FolderImporter(blobStore, rowStore, descriptionCache)


  /* ====== http requests ====== */

  lazy val requests = {
    val executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                          1, TimeUnit.SECONDS,
                                          new SynchronousQueue())
    new OkHttpRequester(5, 1, executor)
  }

  /* ====== main webserver ====== */

  lazy val contentLoader = new ContentLoader(narratorCache, rowStore, descriptionCache)
  lazy val serverPages   = new ServerPages()
  lazy val interactions  = new Interactions(contentLoader = contentLoader,
                                            narratorCache = narratorCache,
                                            narrationHint = narrationHint,
                                            userStore = userStore,
                                            requestUtil = requests)

  lazy val server: JavalinServer =
    new JavalinServer(blobStore = blobStore,
                      terminate = () => terminateEverything(true),
                      pages = serverPages,
                      folderImporter = folderImporter,
                      interactions = interactions,
                      staticPath = staticDir,
                      urlPrefix = urlPrefix,
                      rowStore = rowStore
                      )

  def startServer() = server.start(interface, port)


  /* ====== clockwork ====== */

  lazy val computeExecutor: ThreadPoolExecutor = {
    val res = new ThreadPoolExecutor(1, 1, 1,
                                     TimeUnit.SECONDS,
                                     new LinkedBlockingQueue[Runnable]())
    res.allowCoreThreadTimeOut(true)
    res
  }

  lazy val computeExecutionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(computeExecutor)

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
      if (force) try {
        rowStore.filterSingleLevelMissing(narrator.id) }
      catch { case NonFatal(e) => Log.Server.warn(s"filering failed: ${e.getMessage}")}
      clockwork.runNarrator(narrator, if (force) 0 else clockwork.dayInMillis * 1)
    }
  }


  /* ====== notifications ====== */

  lazy val narrationHint: Evt[(Narrator, Boolean)] = Evt[(Narrator, Boolean)]()

  def terminateEverything(startedServer: Boolean) = {
    new java.util.Timer().schedule(new TimerTask {
      override def run(): Unit = {
        crawl.shutdown()
        computeExecutor.shutdown()
        requests.executorService.shutdown()
        if (startedServer) server.stop()
      }
    }, 100)

  }


}
