package viscel.scribe.narration

import org.jsoup.nodes.Document
import viscel.selection.Report
import org.scalactic.{Every, Or}
import org.scalactic.TypeCheckedTripleEquals._

trait Narrator {
	def id: String
	def name: String
	def archive: List[Story]
	def wrap(doc: Document, more: More): List[Story] Or Every[Report]
	final override def equals(other: Any) = other match {
		case o: Narrator => id === o.id
		case _ => false
	}
	final override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}


