package viscel.crawler

import rescala.Evt
import rescala.propagation.Engines.default
import spray.client.pipelining.SendReceive
import spray.http.{HttpResponse, HttpRequest}
import viscel.database._
import viscel.narration.Narrator
import viscel.store.Collection
import viscel.{Deeds, Log}

import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


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

	def handleHints(hints: Evt[Collection], ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = hints += { col =>
		val id = neo.tx { col.id(_) }
		if (runners.contains(id)) Log.trace(s"$id has running job")
		else {
			Log.info(s"got hint $id")
			Narrator.get(id) match {
				case None => Log.warn(s"unkonwn core $id")
				case Some(core) =>
					val runner = new Runner(core, iopipe, col, neo, ec)
					ensureRunner(core.id, runner, ec)
			}
		}
	}

}
