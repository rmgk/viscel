package viscel.core

import org.scalactic.Every
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.ErrorMessage
import spray.http.{HttpResponse, MediaType}
import viscel.store._
import org.scalactic.Or

sealed trait Content {
	def matches(node: Option[ViscelNode]): Boolean
}

case class ChapterContent(name: String, props: Map[String, String] = Map()) extends Content {
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


