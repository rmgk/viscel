package viscel.description

import org.scalactic.TypeCheckedTripleEquals._
import viscel.store._
import viscel.core._

sealed trait Content {
	def matches(node: Option[ViscelNode]): Boolean
	implicit def toDescription: Description = Structure(payload = this)
}

case class Chapter(name: String, props: Map[String, String] = Map()) extends Content {
	override def matches(node: Option[ViscelNode]): Boolean = node match {
		case Some(chapterNode: ChapterNode) => chapterNode.name === name
		case _ => false
	}
}

case class ElementContent(source: AbsUri, origin: AbsUri, props: Map[String, String] = Map()) extends Content {
	override def matches(node: Option[ViscelNode]): Boolean = node match {
		case Some(elementNode: ElementNode) => (elementNode.source === source) && (elementNode.origin === origin)
		case _ => false
	}
}

case object EmptyContent extends Content {
	override def matches(node: Option[ViscelNode]): Boolean = node.isEmpty
}


