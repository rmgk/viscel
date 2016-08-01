package viscel

import java.nio.file.Path
import java.util.{Timer, TimerTask}

import org.scalactic.{Bad, Good}
import viscel.crawl.Crawl
import viscel.narration.Narrators
import viscel.scribe.Scribe
import viscel.scribe.narration.Narrator
import viscel.scribe.store.Json
import viscel.shared.Log
import viscel.store.Users

import scala.collection.immutable.Map
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


class Clockwork(path: Path, crawl: Crawl, scribe: Scribe) {

	val dayInMillis = 24L * 60L * 60L * 1000L


	val timer: Timer = new Timer(true)
	val delay: Long = 0
	val period: Long = 60 * 60 * 1000 // every hour

	def recheckPeriodically(): Unit = {
		timer.scheduleAtFixedRate(new TimerTask {
			override def run(): Unit = synchronized {
				Log.info("running scheduled updates")
				Users.all() match {
					case Bad(err) => Log.error(s"could not load bookmarked collections: $err")
					case Good(users) =>
						val narrators = users.flatMap(_.bookmarks.keySet).distinct.flatMap(Narrators.get)
						narrators.foreach {runNarrator(_, 7 * dayInMillis)}
				}
			}
		}, delay, period)

	}

	def runNarrator(n: Narrator, recheckInterval: Long) = {
		if (needsRecheck(n.id, recheckInterval)) {
			crawl.runForNarrator(n).onComplete {
				case Failure(t) =>
					Log.error(s"recheck failed with $t")
					t.printStackTrace()
				case Success(true) =>
					Log.info(s"update ${n.id} complete")
					updateDates(n.id)
				case Success(false) =>
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
		Log.trace(s"calculating recheck for $id")
		val lastRun = updateTimes.get(id)
		val time = System.currentTimeMillis()
		lastRun.isEmpty || (time - lastRun.get > recheckInterval)
	}

}
