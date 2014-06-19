package viscel.core

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import viscel.description.{Pointer, Description}

trait Core {
	def id: String
	def name: String
	def archive: List[Description]
	def wrap(doc: Document, pd: Pointer): List[Description]
	override def equals(other: Any) = other match {
		case o: Core => id === o.id
		case _ => false
	}
	override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}
