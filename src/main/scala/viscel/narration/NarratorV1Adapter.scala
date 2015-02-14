package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import org.scalactic.Accumulation._
import viscel.compat.v1.Upgrader.translateStory
import viscel.compat.v1.Upgrader
import viscel.scribe.narration.{Problem, Story, More, Narrator, Asset}
import viscel.compat.v1.{Story => OldStory}

import scala.collection.immutable.Map

class NarratorV1Adapter(wrapped: NarratorV1) extends Narrator {

	override def id: String = wrapped.id
	override def name: String = wrapped.name
	override def archive: List[Story] = wrapped.archive map translateStory map (_.get)
	override def wrap(doc: Document, more: More): List[Story] Or Every[Problem] =
		wrapped.wrap(doc, OldStory.More.Kind(more.data.head)).map(translateStory).combined.badMap(_.map(Problem.apply))
}
