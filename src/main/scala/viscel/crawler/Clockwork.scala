package viscel.crawler

import rescala.Evt
import rescala.propagation.Engines.default
import spray.client.pipelining.SendReceive
import spray.http.{HttpResponse, HttpRequest}
import viscel.database._
import viscel.narration.Narrator
import viscel.shared.Story.Narration
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

	def handleHints(hints: Evt[(Narrator, Boolean)], ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = hints += { case (narrator, force) =>
		val id = narrator.id
		if (runners.contains(id)) Log.trace(s"$id has running job")
		else {
			Log.info(s"got hint $id")
			val runner = neo.tx { implicit ntx =>
				val collection = Collection.findAndUpdate(narrator)
				if (!force && !Archive.needsRecheck(collection.self)) None
				else {
					Some(new Runner(narrator, iopipe, collection, neo, ec))
				}
			}
			runner.foreach{ ensureRunner(id, _, ec) }
		}
	}

}
