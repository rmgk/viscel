package viscel.crawler

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import org.scalactic.ErrorMessage
import rescala.events.ImperativeEvent
import spray.client.pipelining.SendReceive
import viscel.Deeds
import viscel.database.Traversal.origin
import viscel.database._
import viscel.narration.Narrator
import viscel.store.{Coin, Collection}

import scala.Predef.any2ArrowAssoc
import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.Predef.exceptionWrapper


object Clockwork extends StrictLogging {

	val jobs: concurrent.Map[String, Runner] = concurrent.TrieMap[String, Runner]()

	def ensureJob(id: String, runner: Runner, collection: Collection, ec: ExecutionContext, iopipe: SendReceive, neo: Neo)(strategy: Collection => Strategy): Unit = {
		jobs.putIfAbsent(id, runner) match {
			case Some(x) => logger.info(s"$id race on job creation")
			case None =>
				logger.info(s"add new job $runner")
				Future.successful[Option[ErrorMessage]](None).flatMap(_ => runner.start(collection, neo)(strategy))(ec).onComplete {
					case Success(res) => Deeds.jobResult(res)
					case Failure(t) => Deeds.jobResult(Some(t.getMessage + "\n" + t.getStackTraceString))
				}(ec)
		}
	}

	def handleHints(hints: ImperativeEvent[Collection], ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = hints += { col =>
		val id = neo.tx { col.id(_) }
		if (jobs.contains(id)) logger.trace(s"$id has running job")
		else Future {
			logger.info(s"got hint $id")
			Narrator.get(id) match {
				case None => logger.warn(s"unkonwn core $id")
				case Some(core) =>
					val job = new Runner(core, iopipe, ec)
					ensureJob(core.id, job, col, ec, iopipe, neo)(Strategy.mainStrategy)
			}
		}(ec).onFailure { case e: Throwable => e.printStackTrace() }(ec)
	}

}
