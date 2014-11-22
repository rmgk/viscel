package viscel.crawler

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import rescala.events.ImperativeEvent
import spray.client.pipelining.SendReceive
import viscel.Deeds
import viscel.database.Traversal.origin
import viscel.narration.Narrator
import viscel.store.Coin
import viscel.store.Collection
import viscel.database._


import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.Predef.any2ArrowAssoc


object Clockwork extends StrictLogging {

	val jobs: concurrent.Map[String, Runner] = concurrent.TrieMap[String, Runner]()

	def unseenNext(shallow: Boolean)(from: Node)(ntx: Ntx): Option[Node] =
		Traversal.findForward {
			case n@Coin.isPage(page) if page.self.to(rel.describes)(ntx).isEmpty => Some(n)
			case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
			case _ => None
		}(from)(ntx)

	def forwardStrategy(start: Node)(select: Ntx => Node => Option[Node]): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] =
			Traversal.findForward(select(ntx))(start)(ntx).map(n => n -> forwardStrategy(n)(select))
	}

	def recheckOld(collection: Collection): Strategy = forwardStrategy(collection.self)(ntx => {
		case n@Coin.isPage(page) if Util.needsRecheck(n)(ntx) || page.self.to(rel.describes)(ntx).isEmpty => Some(n)
		case n@Coin.isAsset(asset) if asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	})


	def ensureJob(id: String, runner: Runner, collection: Collection, ec: ExecutionContext, iopipe: SendReceive, neo: Neo)(strategy: Collection => Strategy): Unit = {
		jobs.putIfAbsent(id, runner) match {
			case Some(x) => logger.info(s"$id race on job creation")
			case None =>
				logger.info(s"add new job $runner")
				runner.start(collection, neo)(strategy).onComplete {
					case Success(res) => Deeds.jobResult(res)
					case Failure(t) => Deeds.jobResult(Some(t.getMessage))
				}(ec)
		}
	}

	def handleHints(hints: ImperativeEvent[Coin], ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = hints += { coin =>
		neo.tx { implicit ntx =>
			Collection.isCollection(origin(coin.self)).map(col => col -> col.id)
		} match {
			case Some((col, id)) =>
				if (jobs.contains(id)) logger.trace(s"$id has running job")
				else Future {
					logger.info(s"got hint $id")
					Narrator.get(id) match {
						case None => logger.warn(s"unkonwn core $id")
						case Some(core) =>
							val job = new Runner(core, iopipe, ec)
							ensureJob(core.id, job, col, ec, iopipe, neo)(recheckOld)
					}
				}(ec)
			case None =>
		}

	}

}
