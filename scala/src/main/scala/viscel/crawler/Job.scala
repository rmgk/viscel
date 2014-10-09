package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import spray.client.pipelining._
import viscel.cores.Core
import viscel.store._
import viscel.store.nodes.{AssetNode, CollectionNode, PageNode}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}


class Job(val core: Core, neo: Neo, iopipe: SendReceive, ec: ExecutionContext) extends StrictLogging {

	val shallow = false

	override def toString: String = core.toString

	def selectNext(from: ArchiveNode): Option[ArchiveNode] = {
		from.findForward {
			case page@PageNode(_) if page.describes.isEmpty => page
			case asset@AssetNode(_) if (!shallow) && asset.blob.isEmpty => asset
		}
	}

	def writeAsset(assetNode: AssetNode)(blob: Network.Blob): Unit = {
		logger.debug(s"$core: received blob, applying to $assetNode")
		val path = Paths.get(viscel.hashToFilename(blob.sha1))
		Files.createDirectories(path.getParent())
		Files.write(path, blob.buffer)
		neo.txs { assetNode.blob = Nodes.create.blob(blob.sha1, blob.mediatype, assetNode.source)(neo) }
	}

	def writePage(pageNode: PageNode)(doc: Document): Unit = {
		logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pageNode")
		ArchiveManipulation.applyDescription(pageNode, core.wrap(doc, pageNode.description))(neo)
	}

	def start(collection: CollectionNode): Future[Unit] = neo.txs {
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
				neo.txs {
					val next = continue(res)
					run(next)
				}
			}(ec)
	}

	def next(node: ArchiveNode): Option[Network.DelayedRequest[ArchiveNode]] = neo.txs {
		selectNext(node) match {
			case None => None
			case Some(asset@AssetNode(_)) => Nodes.find.blob(asset.source)(neo) match {
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
		case page@PageNode(_) => Network.documentRequest(page.location).map { writePage(page) }
		case asset@AssetNode(_) => Network.blobRequest(asset.description).map { writeAsset(asset) }
	}

}
