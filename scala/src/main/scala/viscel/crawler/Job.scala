package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import spray.client.pipelining._
import viscel.crawler.Result.Done
import viscel.narration.Narrator
import viscel.database.{Neo, ArchiveManipulation, Ntx, NodeOps, Traversal, rel}
import viscel.store.coin.{Asset, Collection, Page}
import viscel.store.{Coin, StoryCoin, Vault}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters.iterableAsScalaIterableConverter


class Job(val core: Narrator, neo: Neo, iopipe: SendReceive, ec: ExecutionContext)(nextStrategy: Node => Ntx => Option[Node]) extends StrictLogging {

	override def toString: String = s"Job(${ core.toString })"

	private def writeAsset(assetNode: Asset)(blob: Network.Blob)(ntx: Ntx): Unit = {
		logger.debug(s"$core: received blob, applying to $assetNode")
		val path = Paths.get(viscel.hashToFilename(blob.sha1))
		Files.createDirectories(path.getParent())
		Files.write(path, blob.buffer)
		neo.tx { implicit ntx => assetNode.blob = Vault.create.blob(blob.sha1, blob.mediatype, assetNode.source) }
	}

	private def writePage(pageNode: Page)(doc: Document)(ntx: Ntx): Unit = {
		logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pageNode")
		neo.tx { implicit ntx => ArchiveManipulation.applyNarration(pageNode.self, core.wrap(doc, pageNode.story)) }
	}

	def start(collection: Collection): Future[Result[Nothing]] = {
		val res = neo.tx { ArchiveManipulation.applyNarration(collection.self, core.archive)(_) }
		res.headOption match {
			case None => Future.successful(Done)
			case Some(archive) => run(archive)
		}
	}

	private def run(node: Node): Future[Result[Nothing]] = nextRequest(node) match {
		case Result.Done => Future.successful(Done)
		case Result.DelayedRequest(request, continue) =>
			Network.getResponse(request, iopipe).flatMap { res =>
				neo.tx { ntx =>
					val next = continue(res)(ntx)
					run(next)
				}
			}(ec)
		case Result.Failed(message) => Future.successful(Result.Failed(message))
	}

	private def nextRequest(node: Node): Result[Ntx => Node] = neo.tx { implicit ntx =>
		nextStrategy(node)(ntx) match {
			case None => Result.Done
			case Some(Coin.isAsset(asset)) => Vault.find.blob(asset.source) match {
				case Some(blob) =>
					logger.info(s"use cached ${ blob.sha1 } for ${ asset.source }")
					asset.blob = blob
					nextRequest(asset.self)
				case None => request(asset.self)(ntx).map { _.andThen(_ => asset.self) }
			}
			case Some(next) => request(next)(ntx).map { _.andThen(_ => next) }
		}
	}

	private def request(node: Node)(ntx: Ntx): Result[Ntx => Unit] = node match {
		case Coin.isPage(page) => Network.documentRequest(page.location(ntx)).map { writePage(page) }
		case Coin.isAsset(asset) => Network.blobRequest(asset.source(ntx), asset.origin(ntx)).map { writeAsset(asset) }
		case other => Result.Failed(s"can only request pages and assets not ${other.getLabels.asScala.toList}")
	}

}
