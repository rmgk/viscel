package viscel.core

import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.store._


sealed trait Description {
	def describes(archive: Option[ArchiveNode]): Boolean
}

object Description {
	def fromOr[T](or: Description Or Every[ErrorMessage]): Description = or.fold(identity, FailedDescription)
}

case class PointerDescription(loc: AbsUri, pagetype: String) extends Description {
	override def describes(archive: Option[ArchiveNode]): Boolean = archive match {
		case Some(pageNode: PageNode) => pageNode.pointerDescription == this
		case _ => false
	}
}

case object EmptyDescription extends Description {
	override def describes(archive: Option[ArchiveNode]): Boolean = archive.isEmpty
}

case class FailedDescription(reason: Every[ErrorMessage]) extends Description {
	override def describes(archive: Option[ArchiveNode]): Boolean = ???
}

case class StructureDescription(payload: Content = EmptyContent, next: Description = EmptyDescription, children: Seq[Description] = Seq()) extends Description {
	override def describes(archive: Option[ArchiveNode]): Boolean = archive match {
		case Some(structureNode: StructureNode) =>
			def nextCorresponds = next.describes(structureNode.next)
			def payloadCorresponds = payload.matches(structureNode.payload)
			def childernCorrespond = {
				val structureChildren = structureNode.children
				(children.size === structureChildren.size) &&
					children.zip(structureChildren).forall { case (child, structureChild) => child.describes(Some(structureChild)) }
			}
			nextCorresponds && payloadCorresponds && childernCorrespond
		case _ => false
	}
}


