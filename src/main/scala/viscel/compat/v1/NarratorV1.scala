package viscel.compat.v1

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import viscel.compat.v1.Story.More.Kind

trait NarratorV1 {
	def id: String
	def name: String
	def archive: List[Story]
	def wrap(doc: Document, kind: Kind): List[Story]
	final override def equals(other: Any) = other match {
		case o: NarratorV1 => id === o.id
		case _ => false
	}
	final override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}


