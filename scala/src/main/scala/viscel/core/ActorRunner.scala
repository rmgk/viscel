package viscel.core

import akka.actor.{Actor, ActorRef}
import akka.pattern.pipe
import org.jsoup.nodes.Document
import spray.client.pipelining.SendReceive
import viscel.core.impl.{ArchiveManipulation, NetworkPrimitives}
import viscel.description.ElementContent
import viscel.store._
import akka.actor.Status.Failure

import scala.concurrent.ExecutionContext.Implicits.global
import scalax.io.Resource

class ActorRunner(val iopipe: SendReceive, val core: Core, val collection: CollectionNode, val clockwork: ActorRef) extends Actor with ArchiveManipulation with NetworkPrimitives {

	override def preStart() = Neo.txs {
		initialDescription(collection, core.archive)
		fixLinkage(ArchiveNode(collection).get, collection)
	}

	def undescribedPages: Seq[PageNode] = ArchiveNode(collection).fold(ifEmpty = List[ArchiveNode]()) { _.flatten }.collect {
		case pn: PageNode if pn.describes.isEmpty => pn
	}.reverse

	def placeholderElements: Seq[ElementNode] = ArchiveNode(collection).fold(ifEmpty = Seq[ElementNode]()) { an =>
		an.flatPayload.collect { case en: ElementNode if en.blob.isEmpty => en }
	}.reverse

	def next(): Unit = Neo.txs {
		placeholderElements.headOption match {
			case Some(en) =>
				logger.info(s"$core: found placeholder element, downloading")
				BlobNode.find(en.source) match {
					case Some(blob) => en.blob = blob; self ! "next"
					case None => getBlob(ElementContent(en.source, en.origin)).map { en -> _ }.pipeTo(self)
				}
			case None =>
				undescribedPages.headOption match {
					case Some(pn) =>
						logger.info(s"$core: undescribed page $pn, downloading")
						getDocument(pn.location).map { pn -> _ }.pipeTo(self)
					case None =>
						self ! "stop"
						clockwork ! Clockwork.Done(core)
				}
		}
	}

	def always: Receive = {
		case (pn: PageNode, doc: Document) =>
			logger.info(s"$core: received ${ doc.baseUri() }, applying to $pn")
			Neo.txs {
				applyDescription(pn, core.wrap(doc, pn.pointerDescription))
				fixLinkage(ArchiveNode(collection).get, collection)
			}
			self ! "next"
		case (en: ElementNode, ed: Blob) =>
			logger.info(s"$core: received blob, applying to $en")
			Resource.fromFile(viscel.hashToFilename(ed.sha1)).write(ed.buffer)
			Neo.txs { en.blob = BlobNode.create(ed.sha1, ed.mediatype, en.source) }
			self ! "next"
		case Failure(throwable) =>
			logger.info(s"failed download core ($core): $throwable")
			clockwork ! Clockwork.Done(core)
		case other => logger.warn(s"unknown message $other")
	}

	def idle: Receive = {
		case "start" =>
			context.become(running orElse always)
			next()
	}

	def running: Receive = {
		case "stop" => context.become(idle orElse always)
		case "next" => next()
	}

	def receive: Actor.Receive = idle orElse always
}
