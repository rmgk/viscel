package viscel

import rescala.default.{Evt, implicitScheduler}
import viscel.crawl.{CrawlScheduler, CrawlServices}
import viscel.narration.Narrator
import viscel.netzi.JvmHttpRequester
import viscel.server.{ContentLoader, Interactions, JettyServer, ServerPages}
import viscel.shared.{JsoniterCodecs, Log}
import viscel.store.{BlobStore, DescriptionCache, JsoniterStorage, NarratorCache, RowStoreV4, Users}

import java.nio.file.{Files, Path}
import java.util.TimerTask
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}
import scala.util.control.NonFatal

class Services(
    relativeBasedir: Path,
    relativeBlobdir: Path,
    val staticDir: Path,
    val urlPrefix: String,
    val interface: String,
    val port: Int
) {

  /* ====== paths ====== */

  def create(p: Path): Path = {
    Files.createDirectories(p)
    p
  }
  val basepath: Path            = relativeBasedir.toAbsolutePath
  val blobdir: Path             = basepath.resolve(relativeBlobdir)
  val metarratorconfigdir: Path = basepath.resolve("metarrators")
  val definitionsdir: Path      = staticDir
  val usersdir: Path            = basepath.resolve("users")
  val cookiePath: Path          = basepath.resolve("cookies.json")
  lazy val db4dir: Path         = create(basepath.resolve("db4"))
  lazy val cachedir: Path       = create(basepath.resolve("cache"))

  /* ====== storage ====== */

  lazy val descriptionCache = new DescriptionCache(cachedir)
  lazy val blobStore        = new BlobStore(blobdir)
  lazy val userStore        = new Users(usersdir)
  lazy val rowStore         = new RowStoreV4(db4dir)
  lazy val narratorCache    = new NarratorCache(metarratorconfigdir, definitionsdir)
  lazy val folderImporter   = new FolderImporter(blobStore, rowStore, descriptionCache)

  /* ====== http requests ====== */

  lazy val requests = {
    val executor = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue())
    val cookies: Map[String, (String, String)] =
      if (Files.exists(cookiePath)) {
        JsoniterStorage.load(cookiePath)(JsoniterCodecs.CookieMapCodec).getOrElse(Map.empty)
      } else Map.empty
    new JvmHttpRequester(executor, cookies)
  }

  /* ====== main webserver ====== */

  lazy val contentLoader = new ContentLoader(narratorCache, rowStore, descriptionCache)
  lazy val serverPages   = new ServerPages()
  lazy val interactions = new Interactions(
    contentLoader = contentLoader,
    narratorCache = narratorCache,
    narrationHint = narrationHint,
    userStore = userStore,
    requestUtil = requests
  )

  lazy val server: JettyServer =
    new JettyServer(
      blobStore = blobStore,
      terminate = () => terminateEverything(true),
      pages = serverPages,
      folderImporter = folderImporter,
      interactions = interactions,
      staticPath = staticDir,
      urlPrefix = urlPrefix,
    )

  def startServer() = server.start(interface, port)

  /* ====== clockwork ====== */

  lazy val crawl: CrawlServices = new CrawlServices(
    blobStore = blobStore,
    requestUtil = requests,
    rowStore = rowStore,
    descriptionCache = descriptionCache,
  )

  lazy val clockwork: CrawlScheduler = new CrawlScheduler(
    path = cachedir.resolve("crawl-times.json"),
    crawlServices = crawl,
    userStore = userStore,
    narratorCache = narratorCache
  )

  def activateNarrationHint() = {
    narrationHint.observe {
      case (narrator, force) =>
        if (force) narratorCache.updateCache()
        descriptionCache.invalidate(narrator.id)
        if (force) then
          try {
            rowStore.filterSingleLevelMissing(narrator.id)
          } catch { case NonFatal(e) => Log.Server.warn(s"filtering failed: ${e.getMessage}") }

        if force || clockwork.needsRecheck(narrator.id, clockwork.dayInMillis * 1)
        then clockwork.runNarrator(narrator, false)

    }
  }

  /* ====== notifications ====== */

  lazy val narrationHint: Evt[(Narrator, Boolean)] = Evt[(Narrator, Boolean)]()

  def terminateEverything(startedServer: Boolean) = {
    new java.util.Timer().schedule(
      new TimerTask {
        override def run(): Unit = {
          crawl.shutdown()
          requests.executorService.shutdown()
          if (startedServer) server.stop()
        }
      },
      100
    )

  }

}
