package viscel.narration.narrators

import org.jsoup.nodes.Document
import viscel.narration.SelectUtil.{cons, elementIntoPointer, extract, queryImageNext, storyFromOr, stringToVurl}
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More.{Archive, Kind, Page}
import viscel.shared.Story.{Chapter, More}

object Inverloch extends Narrator {
	override def id: String = "NX_Inverloch"
	override def name: String = "Inverloch"
	override def archive: List[Story] = Range.inclusive(1, 5).map(i => More(s"http://inverloch.seraph-inn.com/volume$i.html", Archive)).toList
	override def wrap(doc: Document, kind: Kind): List[Story] = kind match {
		case Archive => storyFromOr(Selection(doc).many("#main p:containsOwn(Chapter)").wrapFlat { chap =>
			cons(
				extract(Chapter(chap.ownText())),
				Selection(chap).many("a").wrapEach(elementIntoPointer(Page)))
		})
		case Page => storyFromOr(queryImageNext("#main > p:nth-child(1) > img", "#main a:containsOwn(Next)", Page)(doc))
	}
}
