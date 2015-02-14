package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import org.scalactic.Accumulation._
import viscel.Upgrader
import viscel.Upgrader.translateStory
import viscel.scribe.narration.{Problem, Story, More, Narrator, Asset}
import viscel.shared.{ Story => OldStory }

import scala.collection.immutable.Map

class NarratorV1Adapter(wrapped: NarratorV1) extends Narrator {

	override def id: String = wrapped.id
	override def name: String = wrapped.name
	override def archive: List[Story] = wrapped.archive map translateStory map (_.get)
	override def wrap(doc: Document, more: More): List[Story] Or Every[Problem] =
		wrapped.wrap(doc, OldStory.More.Kind(more.data.head)).map(translateStory).combined.badMap(_.map(Problem.apply))
}
