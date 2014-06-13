package viscel.newCore

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._

trait Core {
	def id: String
	def name: String
	def archive: Description
	def wrap(doc: Document, pd: PointerDescription): Description
	override def equals(other: Any) = other match {
		case o: Core => id === o.id
		case _ => false
	}
	override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}
