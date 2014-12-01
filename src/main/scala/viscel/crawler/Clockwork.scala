package viscel.crawler

import com.typesafe.scalalogging.slf4j.StrictLogging
import rescala.Evt
import rescala.propagation.Engines.default
import spray.client.pipelining.SendReceive
import viscel.Deeds
import viscel.database._
import viscel.narration.Narrator
import viscel.store.Collection

import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object Clockwork extends StrictLogging {

	val runners: concurrent.Map[String, Runner] = concurrent.TrieMap[String, Runner]()

	def ensureRunner(id: String, runner: Runner, ec: ExecutionContext): Unit = {
		runners.putIfAbsent(id, runner) match {
			case Some(x) => logger.info(s"$id race on job creation")
			case None =>
				logger.info(s"add new job $runner")
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
		if (runners.contains(id)) logger.trace(s"$id has running job")
		else Future {
			logger.info(s"got hint $id")
			Narrator.get(id) match {
				case None => logger.warn(s"unkonwn core $id")
				case Some(core) =>
					val runner = new Runner(core, iopipe, col, neo, ec)
					ensureRunner(core.id, runner, ec)
			}
		}(ec).onFailure { case e: Throwable => e.printStackTrace() }(ec)
	}

}
