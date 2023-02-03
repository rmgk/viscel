package viscel.crawl

import de.rmgk.delay.Async
import viscel.narration.Narrator
import viscel.shared.{JsoniterCodecs, Vid}
import viscel.store.{JsoniterStorage, NarratorCache, Users}

import java.net.SocketTimeoutException
import java.nio.file.Path
import java.util
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.*
import java.util.{Timer, TimerTask}
import scala.annotation.tailrec
import scala.collection.immutable.{Map, Queue}
import scala.collection.mutable
import scala.util.{Failure, Success}

class CrawlScheduler(
    path: Path,
    crawlServices: CrawlServices,
    userStore: Users,
    narratorCache: NarratorCache
) {

  val log = viscel.shared.Log.Crawl

  val dayInMillis: Long = 24L * 60L * 60L * 1000L

  val timer: Timer = new Timer(true)
  val delay: Long  = 0L
  val period: Long = 60L * 60L * 1000L // every hour

  var running: Set[Vid] = Set.empty

  def recheckPeriodically(): Unit = {
    timer.scheduleAtFixedRate(
      new TimerTask {
        override def run(): Unit =
          synchronized {
            log.info("schedule updates")
            val narrators = userStore.allBookmarks().flatMap(narratorCache.get)
            val filtered  = narrators.filter(n => needsRecheck(n.id, 7 * dayInMillis))
            queued.addAll(filtered)
          }
          fillQueue()
      },
      delay,
      period
    )
  }

  val queued: mutable.Queue[Narrator] = mutable.Queue()
  val queueRunning                    = new Semaphore(5)

  def fillQueue(): Unit =
    while queueRunning.tryAcquire()
    do
      synchronized { queued.removeHeadOption() } match
        case None =>
        case Some(nar) =>
          runNarrator(nar, true)

  def runNarrator(narrator: Narrator, free: Boolean): Unit =
    synchronized {
      if (!running.contains(narrator.id)) {

        Async[Any].resource(
          CrawlScheduler.this.synchronized { running = running + narrator.id },
          _ => CrawlScheduler.this.synchronized { running = running - narrator.id }
        ) { _ =>
          crawlServices.startCrawling(narrator).bind
          log.info(s"[${narrator.id}] update complete")
          updateDates(narrator.id)
        }.run(using ()) { res =>
          synchronized { if (queued.isEmpty && running.isEmpty) System.gc() }
          if free then queueRunning.release()
          fillQueue()
          res match
            case Failure(error) => logError(narrator)(error)
            case Success(())    =>
        }
      }
    }

  private def logError(narrator: Narrator): Throwable => Unit = {
    case RequestException(uri, status) =>
      log.error(s"[${narrator.id}] error request: $uri failed: $status")
    case WrappingException(request, response, reports) =>
      log.error(
        s"""↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
           |$narrator
           |  ${request.href.uriString()} ${request.context.mkString("(", ",", ")")}
           |  ${response.location.uriString()} (${response.mime})
           |  ${reports.describe}
           |↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑""".stripMargin
      )

    case ce: CancellationException =>
      log.info(s"[${narrator.id}] update cancelled ${ce.getMessage}")
    case se: SocketTimeoutException =>
      log.error(s"[${narrator.id}] recheck failed with $se")
    case t =>
      log.error(s"[${narrator.id}] recheck failed with $t")
      t.printStackTrace()
  }

  private var updateTimes: Map[Vid, Long] = {
    JsoniterStorage.load[Map[Vid, Long]](path)(JsoniterCodecs.MapVidLongCodec).fold(
      err => {
        log.error(s"could not load $path: $err")
        Map()
      },
      identity
    )
  }

  def updateDates(id: Vid): Unit =
    synchronized {
      val time = System.currentTimeMillis()
      updateTimes = updateTimes.updated(id, time)
      JsoniterStorage.store(path, updateTimes)(JsoniterCodecs.MapVidLongCodec)
    }

  def needsRecheck(id: Vid, recheckInterval: Long): Boolean =
    synchronized {
      val lastRun = updateTimes.get(id)
      val time    = System.currentTimeMillis()
      val res     = lastRun.isEmpty || (time - lastRun.get > recheckInterval)
      log.trace(s"calculating recheck for $id: $res")
      res
    }

}
