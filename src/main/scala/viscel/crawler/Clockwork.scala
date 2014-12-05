package viscel.crawler

import rescala.Evt
import rescala.propagation.Engines.default
import spray.client.pipelining.SendReceive
import viscel.database._
import viscel.narration.Narrator
import viscel.store.Collection
import viscel.{Deeds, Log}

import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object Clockwork {

	val runners: concurrent.Map[String, Runner] = concurrent.TrieMap[String, Runner]()

	def ensureRunner(id: String, runner: Runner, ec: ExecutionContext): Unit = {
		runners.putIfAbsent(id, runner) match {
			case Some(x) => Log.info(s"$id race on job creation")
			case None =>
				Log.info(s"add new job $runner")
				Future.successful(None).flatMap(_ => runner.start())(ec).onComplete {
					case Success(res) => Deeds.jobResult(res)
					case Failure(t) =>
						t.printStackTrace()
						Deeds.jobResult(List(t.getMessage))
				}(ec)
		}
	}

	def handleHints(hints: Evt[Collection], ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = hints += { col =>
		val id = neo.tx { col.id(_) }
		if (runners.contains(id)) Log.trace(s"$id has running job")
		else Future {
			Log.info(s"got hint $id")
			Narrator.get(id) match {
				case None => Log.warn(s"unkonwn core $id")
				case Some(core) =>
					val runner = new Runner(core, iopipe, col, neo, ec)
					ensureRunner(core.id, runner, ec)
			}
		}(ec).onFailure { case e: Throwable => e.printStackTrace() }(ec)
	}

}
