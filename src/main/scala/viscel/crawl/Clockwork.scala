package viscel.crawl

import java.nio.file.Path
import java.util.{Timer, TimerTask}

import org.scalactic.{Bad, Good}
import viscel.narration.{Narrator, Narrators}
import viscel.scribe.{Json, Scribe}
import viscel.shared.Log
import viscel.store.Users

import scala.collection.immutable.Map
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


class Clockwork(path: Path, scribe: Scribe, ec: ExecutionContext, requestUtil: RequestUtil) {

	val dayInMillis = 24L * 60L * 60L * 1000L


	val timer: Timer = new Timer(true)
	val delay: Long = 0
	val period: Long = 60 * 60 * 1000 // every hour

	var running: Map[String, Crawl] = Map()

	def recheckPeriodically(): Unit = {
		timer.scheduleAtFixedRate(new TimerTask {
			override def run(): Unit = synchronized {
				Log.info("running scheduled updates")
				Users.all() match {
					case Bad(err) => Log.error(s"could not load bookmarked collections: $err")
					case Good(users) =>
						val narrators = users.flatMap(_.bookmarks.keySet).distinct.flatMap(Narrators.get)
						//val narrators = List(Narrators.get("NX_Inverloch")).flatten
						narrators.foreach {runNarrator(_, 7 * dayInMillis)}
				}
			}
		}, delay, period)

	}

	def runNarrator(n: Narrator, recheckInterval: Long) = synchronized {
		if (needsRecheck(n.id, recheckInterval) && !running.contains(n.id)) {
			val crawl = new Crawl(n, scribe, requestUtil)(ec)
			running = running.updated(n.id, crawl)
			crawl.start().onComplete { result =>
				Clockwork.this.synchronized(running = running - n.id)
				result match {
					case Failure(RequestException(request, response)) =>
						Log.error(s"[${n.id}] error request: ${request.uri} failed: ${response.status}")
					case Failure(t) =>
						Log.error(s"[${n.id}] recheck failed with $t")
						t.printStackTrace()
					case Success(true) =>
						Log.info(s"[${n.id}] update complete")
						updateDates(n.id)
					case Success(false) =>
				}
			}
		}
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
