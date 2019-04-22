package viscel.crawl

import java.nio.file.Path
import java.util.{Timer, TimerTask}

import viscel.narration.Narrator
import viscel.shared.Vid
import viscel.store.{Json, NarratorCache, Users}

import scala.collection.immutable.Map
import scala.concurrent.ExecutionContext


class CrawlScheduler(path: Path,
                     crawlServices: CrawlServices,
                     ec: ExecutionContext,
                     userStore: Users,
                     narratorCache: NarratorCache,
                    ) {

  val log = viscel.shared.Log.Crawl

  val dayInMillis: Long = 24L * 60L * 60L * 1000L


  val timer : Timer = new Timer(true)
  val delay : Long  = 0L
  val period: Long  = 60L * 60L * 1000L // every hour

  var running: Set[Vid] = Set.empty

  def recheckPeriodically(): Unit = {
    timer.scheduleAtFixedRate(new TimerTask {
      override def run(): Unit = synchronized {
        log.info("schedule updates")
        val narrators = userStore.allBookmarks().flatMap(narratorCache.get)
        narrators.foreach {runNarrator(_, 7 * dayInMillis)}
      }
    }, delay, period)
  }


  def runNarrator(narrator: Narrator, recheckInterval: Long): Unit = synchronized {
    if (!running.contains(narrator.id) && needsRecheck(narrator.id, recheckInterval)) {

      running = running + narrator.id
      implicit val iec: ExecutionContext = ec

      val fut = crawlServices.startCrawling(narrator)
                .andThen { case _ => CrawlScheduler.this.synchronized(running = running - narrator.id) }
      fut.failed.foreach(logError(narrator))
      fut.foreach { _ =>
        log.info(s"[${narrator.id}] update complete")
        updateDates(narrator.id)
      }
      fut.onComplete { _ => synchronized {if (running.isEmpty) System.gc()} }
    }
  }

  private def logError(narrator: Narrator): Throwable => Unit = {
    case RequestException(uri, status)    =>
      log.error(s"[${narrator.id}] error request: $uri failed: $status")
    case WrappingException(link, reports) =>
      log.error(
        s"""↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
           |$narrator
           |  failed on ${link.href.uriString} (${if (link.context.nonEmpty) s", ${link.context}" else ""}):
           |  ${reports.map {_.describe}.mkString("\n  ")}
           |↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑""".stripMargin)
    case t                                =>
      log.error(s"[${narrator.id}] recheck failed with $t")
      t.printStackTrace()
  }

  private var updateTimes: Map[Vid, Long] = Json.load[Map[Vid, Long]](path).fold(x => x, err => {
    log.error(s"could not load $path: $err")
    Map()
  })

  def updateDates(id: Vid): Unit = synchronized {
    val time = System.currentTimeMillis()
    updateTimes = updateTimes.updated(id, time)
    Json.store(path, updateTimes)
  }

  def needsRecheck(id: Vid, recheckInterval: Long): Boolean = synchronized {
    val lastRun = updateTimes.get(id)
    val time = System.currentTimeMillis()
    val res = lastRun.isEmpty || (time - lastRun.get > recheckInterval)
    log.trace(s"calculating recheck for $id: $res")
    res
  }

}
