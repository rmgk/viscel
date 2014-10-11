package viscel.crawler

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import rescala.events.ImperativeEvent
import spray.client.pipelining._
import viscel.Deeds
import viscel.database.Traversal.origin
import viscel.narration.Narrator
import viscel.store.{StoryCoin, Coin}
import viscel.store.coin.Collection
import viscel.database.{Neo, Ntx, NodeOps, Traversal, rel}


import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.Predef.any2ArrowAssoc


object Clockwork extends StrictLogging {

	val jobs: concurrent.Map[String, Job] = concurrent.TrieMap[String, Job]()

	def unseenNext(shallow: Boolean)(from: Node)(ntx: Ntx): Option[Node] =
		Traversal.findForward {
			case n@Coin.isPage(page) if page.self.to(rel.describes)(ntx).isEmpty => Some(n)
			case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
			case _ => None
		}(from)(ntx)

	def recheckOld(time: Long)(from: Node)(ntx: Ntx): Option[Node] =
		Traversal.findForward {
			case n@Coin.isPage(page) if page.self.get[Long]("last_update")(ntx).filter(System.currentTimeMillis() - _ < time).isEmpty => Some(n)
			case _ => None
		}(from)(ntx)

	def ensureJob(id: String, job: Job, collection: Collection, ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = {
		jobs.putIfAbsent(id, job) match {
			case Some(x) => logger.info(s"$id race on job creation")
			case None =>
				logger.info(s"add new job $job")
				job.start(collection).onComplete {
					case Success(res) => Deeds.jobResult(res)
					case Failure(t) => Deeds.jobResult(Result.Failed(t.getMessage))
				}(ec)
		}
	}

	def handleHints(hints: ImperativeEvent[Coin], ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = hints += { coin =>
		neo.tx { implicit ntx =>
			Coin.isCollection(origin(coin.self)).map(col => col -> col.id)
		} match {
			case Some((col, id)) =>
				if (jobs.contains(id)) logger.trace(s"$id has running job")
				else Future {
					logger.info(s"got hint $id")
					Narrator.get(id) match {
						case None => logger.warn(s"unkonwn core $id")
						case Some(core) =>
							val job = new Job(core, neo, iopipe, ec)(unseenNext(shallow = false))
							ensureJob(core.id, job, col, ec, iopipe, neo)
					}
				}(ec)
			case None =>
		}

	}

}
