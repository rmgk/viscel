package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{Every, Or}
import viscel.scribe.{Link, WebContent}
import viscel.selection.Report

trait Narrator {
	def id: String
	def name: String
	def archive: List[WebContent]
	def wrap(doc: Document, more: Link): List[WebContent] Or Every[Report]
	final override def equals(other: Any) = other match {
		case o: Narrator => id === o.id
		case _ => false
	}
	final override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}


