package viscel.crawler

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import org.scalactic.ErrorMessage
import spray.client.pipelining.SendReceive
import viscel.database.{ArchiveManipulation, Neo, Ntx}
import viscel.narration.Narrator
import viscel.store.{Coin, Collection}

import scala.annotation.tailrec
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.{ExecutionContext, Future}


class Runner(core: Narrator, iopipe: SendReceive, ec: ExecutionContext) extends StrictLogging {

	override def toString: String = s"Job(${ core.toString })"

	def start(collection: Collection, neo: Neo)(createStrategy: Collection => Strategy): Future[List[ErrorMessage]] = {
		neo.tx { ArchiveManipulation.applyNarration(collection.self, core.archive)(_) }
		val strategy = createStrategy(collection)
		run(strategy, neo)
	}

	private def run(initialStrategy: Strategy, neo: Neo): Future[List[ErrorMessage]] = {
		@tailrec
		def go(strategy: Strategy, neo: Neo): Future[List[ErrorMessage]] = {
			neo.tx { strategy.run(core, _) } match {
				case Result.Done(message) => Future.successful(Nil)
				case Result.Failed(messages) => Future.successful(messages)
				case Result.Continue(nextStrategy) => go(nextStrategy, neo)
				case Result.DelayedRequest(request, continue) =>
					IOUtil.getResponse(request, iopipe).flatMap { res =>
						run(neo.tx { continue(res) }, neo)
					}(ec)
			}
		}
		go(initialStrategy, neo)
	}

}
