package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.scribe.narration.{More, Story}
import viscel.selection.Report

trait Narrator extends viscel.scribe.narration.Narrator {
	final override def wrapped(doc: Document, more: More): Or[List[Story], Every[ErrorMessage]] = wrap(doc, more).badMap(_.map(_.describe))
	def wrap(doc: Document, more: More): List[Story] Or Every[Report]
}


