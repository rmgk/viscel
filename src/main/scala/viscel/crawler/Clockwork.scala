package viscel.crawler

import java.nio.file.Path

import spray.client.pipelining.SendReceive
import viscel.database._
import viscel.narration.Narrator
import viscel.store.Json
import viscel.{Log, Viscel}

import scala.collection.concurrent
import scala.collection.immutable.Map
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
				runner.init()
				ec.execute(runner)
		}
	}

	private val dayInMillis = 24L * 60L * 60L * 1000L

	def runForNarrator(narrator: Narrator, recheckInterval: Long, iopipe: SendReceive, neo: Neo, ec: ExecutionContext): Unit = {
		val id = narrator.id
		if (runners.contains(id)) Log.trace(s"$id has running job")
		else if (!needsRecheck(narrator, recheckInterval)) Log.trace(s"$id does not need recheck")
		else {
			Log.info(s"update ${ narrator.id }")
			val runner = neo.tx { implicit ntx =>
				val collection = Books.findAndUpdate(narrator)
				new Runner(narrator, iopipe, collection, neo, ec)
			}
			ensureRunner(id, runner, ec)
		}
	}

	def handleHints(ec: ExecutionContext, iopipe: SendReceive, neo: Neo): (Narrator, Boolean) => Unit = {
		case (narrator, force) =>
			Log.info(s"got hint ${ narrator.id }")
			runForNarrator(narrator, if (force) 0 else dayInMillis / 4, iopipe, neo, ec)
	}


	private val path: Path = Viscel.basepath.resolve("data/updateTimes.json")
	private var updateTimes: Map[String, Long] = Json.load(path).fold(x => x, err => {
		Log.error(s"could not load $path: $err")
		Map()
	})

	def updateDates(nar: Narrator): Unit = synchronized {
		val time = System.currentTimeMillis()
		updateTimes = updateTimes.updated(nar.id, time)
		Json.store(path, updateTimes)
	}

	def needsRecheck(nar: Narrator, recheckInterval: Long): Boolean = synchronized {
		Log.trace(s"calculating recheck for $nar")
		val lastRun = updateTimes.get(nar.id)
		val time = System.currentTimeMillis()
		lastRun.isEmpty || (time - lastRun.get > recheckInterval)
	}

}
