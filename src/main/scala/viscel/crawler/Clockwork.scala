package viscel.crawler

import java.util.{Timer, TimerTask}

import org.scalactic.{Bad, Good}
import spray.client.pipelining.SendReceive
import viscel.Log
import viscel.database._
import viscel.narration.{Narrator, Narrators}
import viscel.store.{Books, Users}

import scala.collection.concurrent
import scala.concurrent.ExecutionContext


object Clockwork {


	val runners: concurrent.Map[String, Runner] = concurrent.TrieMap[String, Runner]()

	def finish(narrator: Narrator, runner: Runner): Unit = {
		runners.remove(narrator.id, runner)
	}

	def ensureRunner(id: String, runner: Runner, ec: ExecutionContext): Unit = {
		runners.putIfAbsent(id, runner) match {
			case Some(x) => Log.info(s"$id race on job creation")
			case None =>
				Log.info(s"add new job $runner")
				runner.init()
				ec.execute(runner)
		}
	}

	private val dayInMillis = 24L * 60L * 60L * 1000L

	def runForNarrator(narrator: Narrator, recheckInterval: Long, iopipe: SendReceive, neo: Neo, ec: ExecutionContext): Unit = {
		val id = narrator.id
		if (runners.contains(id)) Log.trace(s"$id has running job")
		else {
			val runner = neo.tx { implicit ntx =>
				val collection = Books.findAndUpdate(narrator)
				if (!Archive.needsRecheck(collection.self, recheckInterval)) None
				else {
					Some(new Runner(narrator, iopipe, collection, neo, ec))
				}
			}
			runner.foreach { ensureRunner(id, _, ec) }
		}
	}

	def handleHints(ec: ExecutionContext, iopipe: SendReceive, neo: Neo): (Narrator, Boolean) => Unit = {
		case (narrator, force) =>
			Log.info(s"got hint ${narrator.id}")
			runForNarrator(narrator, if (force) 0 else dayInMillis / 4, iopipe, neo, ec)
	}


	val timer: Timer = new Timer(true)
	val delay: Long = 0
	val period: Long = 60 * 60 * 1000 // every hour

	def recheckPeriodically(ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = {
		timer.scheduleAtFixedRate(new TimerTask {
			override def run(): Unit = synchronized {
				Log.info("running scheduled updates")
				Users.all() match {
					case Bad(err) => Log.error(s"could not load bookmarked collections: $err")
					case Good(users) =>
						val narrators = users.flatMap(_.bookmarks.keySet).distinct.map(Narrators.get).flatten
						narrators.foreach { n =>
							Log.info(s"update ${n.id}")
							runForNarrator(n, dayInMillis * 7, iopipe, neo, ec) }
				}
			}
		}, delay, period)

	}

}
