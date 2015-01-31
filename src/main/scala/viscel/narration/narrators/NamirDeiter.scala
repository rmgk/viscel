package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Good
import viscel.narration.SelectUtil.{elementIntoChapterPointer, elementIntoPointer, queryImage, queryImageInAnchor, storyFromOr, stringToVurl}
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More
import viscel.shared.Story.More.{Archive, Issue, Kind, Page}

object NamirDeiter extends Narrator {
	override def id: String = "NX_NamirDeiter"
	override def name: String = "Namir Deiter"
	override def archive: List[Story] = More(s"http://www.namirdeiter.com/archive/index.php?year=1", Archive) :: Nil

	override def wrap(doc: Document, kind: Kind): List[Story] = kind match {
		case Archive => wrap(doc, Issue) ::: storyFromOr(Selection(doc).many("body > center > div > center > h2 > a").wrapEach(elementIntoPointer(Issue)))
		case Issue => storyFromOr(Selection(doc).many("table #arctitle > a").wrapFlat(elementIntoChapterPointer(Page)))
		case Page => storyFromOr(
			if (doc.baseUri() == "http://www.namirdeiter.com/comics/index.php?date=20020819") Good(Nil)
			else if (doc.baseUri() == "http://www.namirdeiter.com/") queryImage("#comic > img")(doc)
			else queryImageInAnchor("body > center > div > center:nth-child(3) > table center img", Page)(doc))
	}
}