package viscel.newCore

import com.typesafe.scalalogging.slf4j.StrictLogging
import scala.Some
import viscel.store._
import org.scalactic.TypeCheckedTripleEquals._

trait ArchiveManipulation extends StrictLogging {

	def flattenElements(an: ArchiveNode): Seq[ElementNode] = ArchiveNode.foldChildren(Seq[ElementNode](), an) {
		case (acc, pn: PageNode) => acc
		case (acc, sn: StructureNode) => sn.describes match {
			case Some(en: ElementNode) => acc :+ en
			case _ => acc
		}
	}

	def createLinkage(an: ArchiveNode, collection: CollectionNode) = {
		def link(vns: List[ViscelNode], cn: ChapterNode): Unit = vns match {
			case Nil => ()
			case vn :: vntail =>
				vn match {
					case cn: ChapterNode =>
						cn.collection = collection
						link(vntail, cn)
					case en: ElementNode =>
						en.chapter = cn
						link(vntail, cn)
				}
		}
		val nodes = an.flatten
		link(nodes.to[List], null)
		val elements = nodes.collect { case en: ElementNode => en }
		elements.reduceLeftOption { (prev, next) =>
			prev.next = next
			next
		}
		elements.headOption.foreach(collection.first = _)
		elements.lastOption.foreach(collection.last = _)

	}

	def createStructureNode(payloadNode: Option[ViscelNode], nextNode: Option[ArchiveNode], childNodes: Seq[ArchiveNode]) = {
		val sn = StructureNode.create()
		payloadNode.foreach(pln => sn.self.createRelationshipTo(pln.self, rel.describes))
		nextNode.foreach(nn => sn.self.createRelationshipTo(nn.self, rel.next))
		childNodes.zipWithIndex.foreach {
			case (cn, index) =>
				val parentRelation = cn.self.createRelationshipTo(sn.self, rel.parent)
				parentRelation.setProperty("pos", index)
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

	def append(cn: CollectionNode, description: Description): Option[ArchiveNode] = {
		val archive = ArchiveNode(cn)
		val newArch = update(archive, description)
		if (archive != newArch) newArch.foreach { an => cn.self.createRelationshipTo(an.self, rel.archive) }
		newArch
	}

	def append(pn: PageNode, description: Description): Option[ArchiveNode] = {
		val oan = update(pn.describes, description)
		if (oan != pn.describes) oan.foreach { an => pn.self.createRelationshipTo(an.self, rel.describes) }
		oan
	}

	def update(archive: Option[ArchiveNode], description: Description): Option[ArchiveNode] = (archive, description) match {
		case (None, desc) => create(desc)

		case (Some(arch), EmptyDescription | FailedDescription(_)) => replace(archive, description)

		case (Some(pn: PageNode), PointerDescription(loc, pagetype)) =>
			if (pn.location === loc && pn.pagetype === pagetype) Some(pn)
			else replace(archive, description)

		case (Some(sn: StructureNode), StructureDescription(payload, next, children)) =>
			val newNext = update(sn.next, next)
			val snChildren = sn.children
			val newChildren = snChildren.map(Some(_)).zipAll(children, None, EmptyDescription).map {
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
			if (cn.name === name) Some(cn)
			else replace(node, payload)

		case (Some(en: ElementNode), ElementDescription(source, origin, props)) =>
			if (en[String]("source") === source.toString && en[String]("origin") === origin.toString) Some(en)
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

		case StructureDescription(payload, next, children) =>
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
