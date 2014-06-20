package viscel.core

import akka.actor.{Actor, ActorRef}
import akka.pattern.pipe
import org.jsoup.nodes.Document
import spray.client.pipelining.SendReceive
import viscel.description.Asset
import viscel.store._
import akka.actor.Status.Failure

import scala.concurrent.ExecutionContext.Implicits.global
import scalax.io.Resource

class ActorRunner(val iopipe: SendReceive, val core: Core, val collection: CollectionNode, val clockwork: ActorRef) extends Actor with NetworkPrimitives {

	override def preStart() = ArchiveManipulation.applyDescription(collection, core.archive)

	def undescribedPage: Option[PageNode] = collection.describes.flatMap(_.findForward{case page: PageNode if page.describes.isEmpty => page})

	def placeholderElement: Option[AssetNode] = collection.describes.flatMap(_.findForward{case asset: AssetNode if asset.blob.isEmpty => asset})

	def next(): Unit = Neo.txs {
		placeholderElement match {
			case Some(en) =>
				logger.debug(s"$core: found placeholder element, downloading")
				BlobNode.find(en.source) match {
					case Some(blob) => en.blob = blob; self ! "next"
					case None => getBlob(en.description).map { en -> _ }.pipeTo(self)
				}
			case None =>
				undescribedPage match {
					case Some(pn) =>
						logger.debug(s"$core: undescribed page $pn, downloading")
						getDocument(pn.location).map { pn -> _ }.pipeTo(self)
					case None =>
						self ! "stop"
						clockwork ! Clockwork.Done(core)
				}
		}
	}

	def always: Receive = {
		case (pn: PageNode, doc: Document) =>
			logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pn")
			ArchiveManipulation.applyDescription(pn, core.wrap(doc, pn.description))
			self ! "next"
		case (en: AssetNode, ed: Blob) =>
			logger.debug(s"$core: received blob, applying to $en")
			Resource.fromFile(viscel.hashToFilename(ed.sha1)).write(ed.buffer)
			Neo.txs { en.blob = BlobNode.create(ed.sha1, ed.mediatype, en.source) }
			self ! "next"
		case Failure(throwable) =>
			logger.warn(s"failed download core ($core): $throwable")
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
