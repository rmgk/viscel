package viscel.scribe.narration

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{ErrorMessage, Every, Or}

trait Narrator {
	def id: String
	def name: String
	def archive: List[Story]
	def wrapped(doc: Document, more: More): List[Story] Or Every[ErrorMessage]
	final override def equals(other: Any) = other match {
		case o: Narrator => id === o.id
		case _ => false
	}
	final override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}


