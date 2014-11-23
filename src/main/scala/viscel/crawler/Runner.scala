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


class Runner(val core: Narrator, iopipe: SendReceive, ec: ExecutionContext) extends StrictLogging {

	override def toString: String = s"Job(${ core.toString })"

	def start(collection: Collection, neo: Neo)(createStrategy: Collection => Strategy): Future[List[ErrorMessage]] = {
		neo.tx { ArchiveManipulation.applyNarration(collection.self, core.archive)(_) }
		val strategy = createStrategy(collection)
		run(strategy, neo)
	}

	private def run(initialStrategy: Strategy, neo: Neo): Future[List[ErrorMessage]] = {
		@tailrec
		def go(strategy: Strategy, neo: Neo): Future[List[ErrorMessage]] = {
			neo.tx { strategy.run(_) } match {
				case None => Future.successful(Nil)
				case Some((node, nextStrategy)) => neo.tx { explore(node)(_) } match {
					case Result.Done => Future.successful(Nil)
					case Result.Failed(message) => Future.successful(List(message))
					case Result.Continue => go(nextStrategy, neo)
					case Result.DelayedRequest(request, continue) =>
						IOUtil.getResponse(request, iopipe).flatMap { res =>
							val errors = neo.tx { continue(res) }
							if (errors.isEmpty) run(nextStrategy, neo)
							else Future.successful(errors)
						}(ec)
				}
			}
		}
		go(initialStrategy, neo)
	}

	private def explore(node: Node)(ntx: Ntx): Result[Ntx => List[ErrorMessage]] = node match {
		case Coin.isPage(page) => IOUtil.documentRequest(page.location(ntx)).map { IOUtil.writePage(core, page) }
		case Coin.isAsset(asset) => IOUtil.blobRequest(asset.source(ntx), asset.origin(ntx)).map { IOUtil.writeAsset(core, asset) }
		case other => Result.Failed(s"can only request pages and assets not ${ other.getLabels.asScala.toList }")
	}

}
