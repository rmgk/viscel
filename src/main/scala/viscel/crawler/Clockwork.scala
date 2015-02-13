package viscel.crawler

import spray.client.pipelining.SendReceive
import viscel.crawler.database._
import viscel.crawler.narration.Narrator

import scala.collection.concurrent
import scala.concurrent.ExecutionContext


object Clockwork {


	val runners: concurrent.Map[String, Runner] = concurrent.TrieMap[String, Runner]()

	def finish(narrator: Narrator, runner: Runner, success: Boolean): Unit = {
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

}
