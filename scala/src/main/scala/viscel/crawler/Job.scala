package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import spray.client.pipelining._
import viscel.narration.Narrator
import viscel.store._
import viscel.store.coin.{Asset, Collection, Page}

import scala.concurrent.{ExecutionContext, Future}


class Job(val core: Narrator, neo: Neo, iopipe: SendReceive, ec: ExecutionContext) extends StrictLogging {

	val shallow = false

	override def toString: String = core.toString

	def selectNext(from: Node): Option[StoryCoin] =
		Traversal.findForward {
			case Coin.isPage(page) if page.self.to(rel.describes).isEmpty => Some(page)
			case Coin.isAsset(asset) if (!shallow) && asset.blob.isEmpty => Some(asset)
			case _ => None
		}(from)

	def writeAsset(assetNode: Asset)(blob: Network.Blob): Unit = {
		logger.debug(s"$core: received blob, applying to $assetNode")
		val path = Paths.get(viscel.hashToFilename(blob.sha1))
		Files.createDirectories(path.getParent())
		Files.write(path, blob.buffer)
		neo.txs { assetNode.blob = Vault.create.blob(blob.sha1, blob.mediatype, assetNode.source)(neo) }
	}

	def writePage(pageNode: Page)(doc: Document): Unit = {
		logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pageNode")
		ArchiveManipulation.applyNarration(pageNode.self, core.wrap(doc, pageNode.story))(neo)
	}

	def start(collection: Collection): Future[Unit] = {
		val res = ArchiveManipulation.applyNarration(collection.self, core.archive)(neo)
		res.headOption match {
			case None => Future.successful(Unit)
			case Some(archive) => run(archive)
		}
	}


	def run(node: Node): Future[Unit] = nextRequest(node) match {
		case None => Future.successful(Unit)
		case Some(Network.DelayedRequest(request, continue)) =>
			Network.getResponse(request, iopipe).flatMap { res =>
				neo.txs {
					val next = continue(res)
					run(next)
				}
			}(ec)
	}

	def nextRequest(node: Node): Option[Network.DelayedRequest[Node]] = neo.txs {
		selectNext(node) match {
			case None => None
			case Some(asset@Asset(_)) => Vault.find.blob(asset.source)(neo) match {
				case Some(blob) =>
					logger.info(s"use cached ${ blob.sha1 } for ${ asset.source }")
					asset.blob = blob
					nextRequest(asset.self)
				case None => Some(request(asset.self).map { _ => asset.self })
			}
			case Some(next) => Some(request(next.self).map { _ => next.self })
		}
	}

	private def request(node: Node): Network.DelayedRequest[Unit] = node match {
		case Coin.isPage(page) => Network.documentRequest(page.location).map { writePage(page) }
		case Coin.isAsset(asset) => Network.blobRequest(asset.source, asset.origin).map { writeAsset(asset) }
	}

}
