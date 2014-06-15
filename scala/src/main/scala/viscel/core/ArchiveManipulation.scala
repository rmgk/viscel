package viscel.core

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.scalactic.TypeCheckedTripleEquals._
import viscel.store._

import scala.annotation.tailrec

trait ArchiveManipulation extends StrictLogging {

	/**
	 * this takes an archive links all elements to their chapters,
	 * all chapters to the collection,
	 * creates the first and last pointers
	 * and links the elements in their order
	 */
	def fixLinkage(an: ArchiveNode, collection: CollectionNode) = {
		@tailrec
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

	def delete(vn: ViscelNode) = vn.deleteNode()

	def replace(archive: Option[ArchiveNode], description: Description): Option[ArchiveNode] = {
		logger.warn(s"archive $archive does not match description $description")
		archive.foreach(delete)
		create(description)
	}

	def replace(archive: Option[ViscelNode], description: Content): Option[ViscelNode] = {
		logger.warn(s"archive $archive does not match description $description")
		archive.foreach(delete)
		createPayload(description)
	}

	def initialDescription(cn: CollectionNode, description: Description): Option[ArchiveNode] = {
		val archive = ArchiveNode(cn)
		val newArch = update(archive, description)
		if (archive !== newArch) newArch.foreach { an => cn.self.createRelationshipTo(an.self, rel.archive) }
		newArch
	}

	def applyDescription(pn: PageNode, description: Description): Option[ArchiveNode] = {
		val oan = update(pn.describes, description)
		if (oan !== pn.describes) oan.foreach { an => pn.self.createRelationshipTo(an.self, rel.describes) }
		oan
	}

	def update(archive: Option[ArchiveNode], description: Description): Option[ArchiveNode] = (archive, description) match {
		case (None, desc) => create(desc)

		case (Some(arch), EmptyDescription | FailedDescription(_)) => replace(archive, description)

		case (Some(pn: PageNode), desc@PointerDescription(loc, pagetype)) =>
			if (pn.pointerDescription === desc) Some(pn)
			else replace(archive, description)

		case (Some(sn: StructureNode), StructureDescription(payload, next, children)) =>
			val newNext = update(sn.next, next)
			val snChildren = sn.children
			val newChildren = snChildren.map(Some(_)).zipAll(children, None, EmptyDescription).map {
				case (archiveChild, descriptionChild) => update(archiveChild, descriptionChild)
			}.flatten
			val newPayload = updatePayload(sn.payload, payload)
			if ((newNext !== sn.next) || (newPayload !== sn.payload) || (newChildren !== snChildren)) {
				sn.deleteNode()
				Some(StructureNode.create(newPayload, newNext, newChildren))
			}
			else Some(sn)

		case (Some(arch), desc) => replace(archive, description)
	}

	def updatePayload(node: Option[ViscelNode], payload: Content): Option[ViscelNode] = (node, payload) match {
		case (None, pay) => createPayload(pay)

		case (Some(arch), EmptyContent) => replace(node, payload)

		case (Some(cn: ChapterNode), ChapterContent(name, props)) =>
			if (cn.name === name) Some(cn)
			else replace(node, payload)

		case (Some(en: ElementNode), ElementContent(source, origin, props)) =>
			if (en.source === source && en.origin === origin) Some(en)
			else replace(node, payload)

		case (_, _) => replace(node, payload)
	}

	def create(desc: Description): Option[ArchiveNode] = desc match {
		case PointerDescription(loc, pagetype) =>
			logger.debug(s"create: page node for $desc")
			Some { PageNode.create(loc, pagetype) }

		case EmptyDescription =>
			logger.debug("create: empty description")
			None

		case FailedDescription(reason) =>
			logger.warn(s"create: failed description $reason")
			None

		case StructureDescription(payload, next, children) =>
			val payNode = createPayload(payload)
			val childNodes = children.flatMap { create }
			val nextNode = create(next)
			Some(StructureNode.create(payNode, nextNode, childNodes))
	}

	def createPayload(payload: Content): Option[ViscelNode] = payload match {
		case ChapterContent(name, props) =>
			Some(ChapterNode.create(name, props.to[Seq]: _*))
		case ElementContent(source, origin, props) =>
			Some(ElementNode.create(source = source, origin = origin, props))
		case EmptyContent => None
	}
}
