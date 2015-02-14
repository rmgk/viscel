package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Good
import viscel.compat.v1.Story
import viscel.compat.v1.Story.More.{Archive, Kind, Page}
import viscel.compat.v1.Story.{Chapter, More}
import viscel.narration.SelectUtil.{cons, elementIntoPointer, extract, queryImageNext, storyFromOr, stringToVurl}
import viscel.narration.{NarratorV1, Selection}

object Inverloch extends NarratorV1 {
	override def id: String = "NX_Inverloch"
	override def name: String = "Inverloch"
	override def archive: List[Story] = Range.inclusive(1, 5).map(i => More(s"http://inverloch.seraph-inn.com/volume$i.html", Archive)).toList
	override def wrap(doc: Document, kind: Kind): List[Story] = kind match {
		case Archive => storyFromOr(Selection(doc).many("#main p:containsOwn(Chapter)").wrapFlat { chap =>
			cons(
				extract(Chapter(chap.ownText())),
				Selection(chap).many("a").wrapEach(elementIntoPointer(Page)))
		})
		case Page => storyFromOr(
			if (doc.baseUri() == "http://inverloch.seraph-inn.com/viewcomic.php?page=765") Good(Nil)
			else queryImageNext("#main > p:nth-child(1) > img", "#main a:containsOwn(Next)", Page)(doc))
	}
}
