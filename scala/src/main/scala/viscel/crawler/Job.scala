package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import spray.client.pipelining._
import viscel.cores.Core
import viscel.store._

import scala.concurrent.{ExecutionContext, Future}


class Job(val core: Core, neo: Neo, iopipe: SendReceive, ec: ExecutionContext) extends StrictLogging {

	val shallow = false

	override def toString: String = core.toString

	def selectNext(from: ArchiveNode): Option[ArchiveNode] = neo.txs {
		from.findForward {
			case PageNode(page) if page.describes.isEmpty => page
			case AssetNode(asset) if (!shallow) && asset.blob.isEmpty => asset
		}
	}

	def writeAsset(assetNode: AssetNode)(blob: Network.Blob): Unit = {
		logger.debug(s"$core: received blob, applying to $assetNode")
		val path = Paths.get(viscel.hashToFilename(blob.sha1))
		Files.createDirectories(path.getParent())
		Files.write(path, blob.buffer)
		neo.txs { assetNode.blob = BlobNode.create(blob.sha1, blob.mediatype, assetNode.source) }
	}

	def writePage(pageNode: PageNode)(doc: Document): Unit = {
		logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pageNode")
		ArchiveManipulation.applyDescription(pageNode, core.wrap(doc, pageNode.description))(neo)
	}

	def start(collection: CollectionNode): Future[Unit] = {
		ArchiveManipulation.applyDescription(collection, core.archive)(neo)
		collection.describes match {
			case None => Future.successful(Unit)
			case Some(archive) => run(archive)
		}
	}

	def run(node: ArchiveNode): Future[Unit] = next(node) match {
		case None => Future.successful(Unit)
		case Some(Network.DelayedRequest(request, continue)) =>
			Network.getResponse(request)(iopipe).flatMap { res =>
				val next = continue(res)
				run(next)
			}(ec)
	}

	def next(node: ArchiveNode): Option[Network.DelayedRequest[ArchiveNode]] = {
		selectNext(node) match {
			case None => None
			case Some(AssetNode(asset)) => BlobNode.find(asset.source) match {
				case Some(blob) =>
					logger.info(s"use cached ${ blob.sha1 } for ${ asset.source }")
					asset.blob = blob
					next(asset)
				case None => Some(request(node).map { _ => asset })
			}
			case Some(next) => Some(request(next).map { _ => next })
		}
	}

	private def request(node: ArchiveNode): Network.DelayedRequest[Unit] = node match {
		case PageNode(page) => Network.documentRequest(page.location).map { writePage(page) }
		case AssetNode(asset) => Network.blobRequest(asset.description).map { writeAsset(asset) }
	}

}
