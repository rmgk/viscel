package viscel.crawl

import java.nio.file.Path
import java.util.{Timer, TimerTask}

import viscel.narration.Narrator
import viscel.scribe.Scribe
import viscel.shared.Log
import viscel.store.{Json, NarratorCache, Users}

import scala.collection.immutable.Map
import scala.concurrent.ExecutionContext


class Clockwork(
	path: Path,
	scribe: Scribe,
	ec: ExecutionContext,
	requestUtil: RequestUtil,
	userStore: Users,
	narratorCache: NarratorCache,
) {

	val dayInMillis: Long = 24L * 60L * 60L * 1000L


	val timer: Timer = new Timer(true)
	val delay: Long = 0L
	val period: Long = 60L * 60L * 1000L // every hour

	var running: Map[String, Crawl] = Map()

	def recheckPeriodically(): Unit = {
		timer.scheduleAtFixedRate(new TimerTask {
			override def run(): Unit = synchronized {
				Log.info("schedule updates")
				val narrators = userStore.allBookmarks().flatMap(narratorCache.get)
				narrators.foreach {runNarrator(_, 7 * dayInMillis)}
			}
		}, delay, period)
	}

	def runNarrator(narrator: Narrator, recheckInterval: Long): Unit = synchronized {
		if (!running.contains(narrator.id) && needsRecheck(narrator.id, recheckInterval)) {

			val book = scribe.findOrCreate(narrator)
			val crawl = new Crawl(narrator, book, requestUtil)(ec)
			running = running.updated(narrator.id, crawl)
			implicit val iec: ExecutionContext = ec

			val fut = crawl.start().andThen { case _ => Clockwork.this.synchronized(running = running - narrator.id) }
			fut.failed.foreach(logError(narrator))
			fut.foreach { _ =>
				Log.info(s"[${narrator.id}] update complete")
				updateDates(narrator.id)
			}
		}
	}

	private def logError(narrator: Narrator): Throwable => Unit = {
		case RequestException(request, response) =>
			Log.error(s"[${narrator.id}] error request: ${request.uri} failed: ${response.status}")
		case WrappingException(link, reports) =>
			Log.error(
				s"""↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
           |$narrator
           |  failed on ${link.ref.uriString()} (${link.policy}${if (link.data.nonEmpty) s", ${link.data}" else ""}):
           |  ${reports.map {_.describe}.mkString("\n  ")}
           |↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑""".stripMargin)
		case t =>
			Log.error(s"[${narrator.id}] recheck failed with $t")
			t.printStackTrace()
	}

	private var updateTimes: Map[String, Long] = Json.load[Map[String, Long]](path).fold(x => x, err => {
		Log.error(s"could not load $path: $err")
		Map()
	})

	def updateDates(id: String): Unit = synchronized {
		val time = System.currentTimeMillis()
		updateTimes = updateTimes.updated(id, time)
		Json.store(path, updateTimes)
	}

	def needsRecheck(id: String, recheckInterval: Long): Boolean = synchronized {
		val lastRun = updateTimes.get(id)
		val time = System.currentTimeMillis()
		val res = lastRun.isEmpty || (time - lastRun.get > recheckInterval)
		Log.trace(s"calculating recheck for $id: $res")
		res
	}

}
