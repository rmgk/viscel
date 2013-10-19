package viscel.newCore

import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import viscel.store._
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ContentType
import scalax.io._
import scala.collection.JavaConversions._
import org.neo4j.graphdb.Direction

trait ActorRunner extends Logging {

	def core: Core = ???
	def collection: CollectionNode = ???

	def flatten(an: ArchiveNode): Seq[ViscelNode] = an.pipe {
		case pn: PageNode => pn.describes.map { flatten }.getOrElse(Seq[ViscelNode]())
		case sn: StructureNode => (sn.describes ++: sn.children.flatMap { flatten }: Seq[ViscelNode]) ++ sn.next.map { flatten }.getOrElse(Seq[ViscelNode]())
	}

	def createLinkage(an: ArchiveNode, collection: CollectionNode) = {
		def link(vns: List[ViscelNode], cn: ChapterNode): Unit = vns match {
			case Nil => ()
			case vn :: vns => {
				vn.self.getRelationships.foreach { rel => if (!rel.isType(viscel.store.rel.describes)) rel.delete() }
				vn match {
					case cn: ChapterNode =>
						collection.append(cn)
						link(vns, cn)
					case en: ElementNode =>
						cn.append(en)
						link(vns, cn)
				}
			}
		}
		link(flatten(an).to[List], null)
	}

	def createStructureNode(payloadNode: Option[ViscelNode], nextNode: Option[ArchiveNode], childNodes: Seq[ArchiveNode]) = {
		val sn = StructureNode.create()
		payloadNode.foreach(pln => sn.self.createRelationshipTo(pln.self, rel.describes))
		nextNode.foreach(nn => sn.self.createRelationshipTo(nn.self, rel.next))
		childNodes.zipWithIndex.foreach {
			case (cn, index) =>
				val parrel = cn.self.createRelationshipTo(sn.self, rel.parent)
				parrel.setProperty("pos", index)
		}
		sn
	}

	def delete(vn: ViscelNode) = vn.deleteNode()

	def replace(archive: Option[ArchiveNode], description: Description): Option[ArchiveNode] = {
		logger.warn(s"archive $archive does not match description $description")
		archive.foreach(delete)
		create(description)
	}

	def replace(archive: Option[ViscelNode], description: Payload): Option[ViscelNode] = {
		logger.warn(s"archive $archive does not match description $description")
		archive.foreach(delete)
		createPayload(description)
	}

	def update(archive: Option[ArchiveNode], description: Description): Option[ArchiveNode] = (archive, description) match {
		case (None, desc) => create(desc)

		case (Some(arch), EmptyDescription | FailedDescription(_)) => replace(archive, description)

		case (Some(pn: PageNode), PointerDescription(loc, pagetype)) =>
			if (pn.location == loc && pn.pagetype == pagetype) Some(pn)
			else replace(archive, description)

		case (Some(sn: StructureNode), desc @ StructureDescription(payload, next, children)) =>
			val newNext = update(sn.next, next)
			val snChildren = sn.children
			val newChildren = (snChildren.map(Some(_)).zipAll(children, None, EmptyDescription)).map {
				case (archiveChild, descriptionChild) => update(archiveChild, descriptionChild)
			}.flatten
			val newPayload = updatePayload(sn.describes, payload)
			if (newNext != sn.next || newPayload != sn.describes || newChildren != snChildren) {
				sn.deleteNode()
				Some(createStructureNode(newPayload, newNext, newChildren))
			}
			else Some(sn)

		case (Some(arch), desc) => replace(archive, description)
	}

	def updatePayload(node: Option[ViscelNode], payload: Payload): Option[ViscelNode] = (node, payload) match {
		case (None, pay) => createPayload(pay)

		case (Some(arch), EmptyDescription) => replace(node, payload)

		case (Some(cn: ChapterNode), ChapterDescription(name, props)) =>
			if (cn.name == name) Some(cn)
			else replace(node, payload)

		case (Some(en: ElementNode), ElementDescription(source, origin, props)) =>
			if (en[String]("source") == source.toString && en[String]("origin") == origin.toString) Some(en)
			else replace(node, payload)

		case (_, _) => replace(node, payload)
	}

	def create(desc: Description): Option[ArchiveNode] = desc match {
		case PointerDescription(loc, pagetype) =>
			logger.info(s"created page node for $desc")
			Some { PageNode.create(loc, pagetype) }

		case EmptyDescription =>
			logger.trace("create empty description")
			None

		case FailedDescription(reason) =>
			logger.trace("create failed description $reason")
			None

		case desc @ StructureDescription(payload, next, children) =>
			val payNode = createPayload(payload)
			val childNodes = children.flatMap { create }
			val nextNode = create(next)
			Some(createStructureNode(payNode, nextNode, childNodes))
	}

	def createPayload(payload: Payload): Option[ViscelNode] = payload match {
		case ChapterDescription(name, props) =>
			Some(ChapterNode.create(name, props.to[Seq]: _*))
		case ElementDescription(source, origin, props) =>
			Some(ElementNode.create(props.to[Seq] :+ ("source" -> source.toString) :+ ("origin" -> origin.toString): _*))
		case EmptyDescription => None
	}

}

