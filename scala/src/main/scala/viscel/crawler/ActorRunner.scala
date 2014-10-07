package viscel.crawler

import java.nio.file.{Paths, Files}

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorRef}
import akka.pattern.pipe
import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import spray.client.pipelining.SendReceive
import viscel.crawler.Messages._
import viscel.store._
import viscel.cores.Core

import scala.Predef.any2ArrowAssoc
import scala.concurrent.ExecutionContext.Implicits.global

class ActorRunner(val iopipe: SendReceive, val core: Core, val collection: CollectionNode, val clockwork: ActorRef) extends Actor with NetworkPrimitives {

	var current: Option[ArchiveNode] = None
	var remaining: Long = 10
	var processingNext: Boolean = false
	var mode: Symbol = 'shallow

	override def preStart() = {
		ArchiveManipulation.applyDescription(collection, core.archive)
		current = collection.describes
	}

	def selectNext(from: Option[ArchiveNode]): Option[ArchiveNode] = from.flatMap(_.findForward {
		case page: PageNode if page.describes.isEmpty => page
		case asset: AssetNode if (mode !== 'shallow) && asset.blob.isEmpty => asset
	})

	def next(): Unit = Neo.txs {
		remaining -= 1
		if (remaining < 0) {
			processingNext = false
			clockwork ! Done(core, timeout = true)
		}
		else selectNext(current) match {
			case None =>
				current = None
				clockwork ! Done(core)
			case found@Some(node) =>
				logger.debug(s"$core: selected next $node")
				current = found
				node match {
					case page: PageNode => getDocument(page.location).map { page -> _ }.pipeTo(self)

					case asset: AssetNode =>
						logger.debug(s"$core: found placeholder element, downloading")
						BlobNode.find(asset.source) match {
							case Some(blob) => asset.blob = blob; doNext()
							case None => getBlob(asset.description).map { asset -> _ }.pipeTo(self)
						}
				}
		}
	}

	def doNext() = { processingNext = false; self ! 'next }

	def receive: Actor.Receive = {
		case 'next => if (!processingNext) { processingNext = true; next() }

		case ArchiveHint(archiveNode) =>
			logger.debug(s"$core received user hint $archiveNode")
			current = Some(archiveNode)
			mode = 'deep
			if (remaining < 10) remaining = 10
			self ! 'next

		case CollectionHint(collectionNode) =>
			logger.debug(s"$core received user hint $collectionNode")
			current = collectionNode.describes
			mode = 'shallow
			remaining = Long.MaxValue
			self ! 'next

		case (pageNode: PageNode, doc: Document) =>
			logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pageNode")
			ArchiveManipulation.applyDescription(pageNode, core.wrap(doc, pageNode.description))
			doNext()

		case (assetNode: AssetNode, blob: Blob) =>
			logger.debug(s"$core: received blob, applying to $assetNode")
			Files.write(Paths.get(viscel.hashToFilename(blob.sha1)), blob.buffer)
			Neo.txs { assetNode.blob = BlobNode.create(blob.sha1, blob.mediatype, assetNode.source) }
			doNext()

		case Failure(throwable) =>
			logger.warn(s"failed download core ($core): $throwable")
			processingNext = false
			clockwork ! Done(core, failed = true)
		case other => logger.warn(s"unknown message $other")
	}
}
