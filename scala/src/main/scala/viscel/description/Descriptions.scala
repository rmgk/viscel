package viscel.description

import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.core._
import viscel.store._


sealed trait Description {
	def describes(archive: Option[ArchiveNode]): Boolean
	def ::(other: Description): Description = other match {
		case EmptyDescription => this
		case structure@Structure(_, EmptyDescription, _) => structure.copy(next = this)
		case _ => Structure(children = List(other), next = this)
	}
	def ::(content: Content): Description = Structure(payload = content, next = this)
}

object Description {
	def fromOr[T](or: Description Or Every[ErrorMessage]): Description = or.fold(identity, FailedDescription)
}

case class Pointer(loc: AbsUri, pagetype: String) extends Description {
	override def describes(archive: Option[ArchiveNode]): Boolean = archive match {
		case Some(pageNode: PageNode) => pageNode.pointerDescription == this
		case _ => false
	}
}

case object EmptyDescription extends Description {
	override def describes(archive: Option[ArchiveNode]): Boolean = archive.isEmpty
	override def ::(other: Description): Description = other
	override def ::(other: Content): Description = Structure(payload = other)
}

case class FailedDescription(reason: Every[ErrorMessage]) extends Description {
	override def describes(archive: Option[ArchiveNode]): Boolean = throw new IllegalStateException(reason.toString())
}

case class Structure(payload: Content = EmptyContent, next: Description = EmptyDescription, children: Seq[Description] = Seq()) extends Description {
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


