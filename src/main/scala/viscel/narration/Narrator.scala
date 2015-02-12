package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{Or, Every}
import org.scalactic.TypeCheckedTripleEquals._
import viscel.shared.Story
import viscel.shared.Story.More.Kind

trait Problem

trait Narrator {
	def id: String
	def name: String
	def archive: List[Story]
	def wrap(doc: Document, kind: Kind): List[Story] Or Every[Problem]
	final override def equals(other: Any) = other match {
		case o: Narrator => id === o.id
		case _ => false
	}
	final override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}


