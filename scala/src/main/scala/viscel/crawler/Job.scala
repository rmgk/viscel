package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import spray.client.pipelining._
import viscel.narration.Narrator
import viscel.database.{Neo, ArchiveManipulation, Ntx, NodeOps, Traversal, rel}
import viscel.store.coin.{Asset, Collection, Page}
import viscel.store.{Coin, StoryCoin, Vault}

import scala.concurrent.{ExecutionContext, Future}


class Job(val core: Narrator, neo: Neo, iopipe: SendReceive, ec: ExecutionContext) extends StrictLogging {

	val shallow = false

	override def toString: String = s"Job(${ core.toString })"

	private def selectNext(from: Node)(implicit ntx: Ntx): Option[StoryCoin] =
		Traversal.findForward {
			case Coin.isPage(page) if page.self.to(rel.describes).isEmpty => Some(page)
			case Coin.isAsset(asset) if (!shallow) && asset.blob.isEmpty => Some(asset)
			case _ => None
		}(from)

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

	def start(collection: Collection): Future[Unit] = {
		val res = neo.tx { ArchiveManipulation.applyNarration(collection.self, core.archive)(_) }
		res.headOption match {
			case None => Future.successful(Unit)
			case Some(archive) => run(archive)
		}
	}

	private def run(node: Node): Future[Unit] = nextRequest(node) match {
		case None => Future.successful(Unit)
		case Some(Network.DelayedRequest(request, continue)) =>
			Network.getResponse(request, iopipe).flatMap { res =>
				neo.tx { ntx =>
					val next = continue(res)(ntx)
					run(next)
				}
			}(ec)
	}

	private def nextRequest(node: Node): Option[Network.DelayedRequest[Ntx => Node]] = neo.tx { implicit ntx =>
		selectNext(node) match {
			case None => None
			case Some(asset@Asset(_)) => Vault.find.blob(asset.source) match {
				case Some(blob) =>
					logger.info(s"use cached ${ blob.sha1 } for ${ asset.source }")
					asset.blob = blob
					nextRequest(asset.self)
				case None => Some(request(asset.self)(ntx).map { _.andThen(_ => asset.self) })
			}
			case Some(next) => Some(request(next.self)(ntx).map { _.andThen(_ => next.self) })
		}
	}

	private def request(node: Node)(ntx: Ntx): Network.DelayedRequest[Ntx => Unit] = node match {
		case Coin.isPage(page) => Network.documentRequest(page.location(ntx)).map { writePage(page) }
		case Coin.isAsset(asset) => Network.blobRequest(asset.source(ntx), asset.origin(ntx)).map { writeAsset(asset) }
	}

}
