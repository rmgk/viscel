package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Good
import viscel.compat.v1.{NarratorV1, SelectUtilV1, SelectionV1, Story}
import viscel.compat.v1.Story.More
import viscel.compat.v1.Story.More.{Archive, Kind, Page}
import SelectUtilV1.{elementIntoChapterPointer, queryImageInAnchor, storyFromOr, stringToVurl}

object YouSayItFirst extends NarratorV1 {
	override def id: String = "NX_YouSayItFirst"
	override def name: String = "You Say It First"
	override def archive: List[Story] = Range.inclusive(1, 9).map(i => More(s"http://www.yousayitfirst.com/archive/index.php?year=$i", Archive)).toList
	override def wrap(doc: Document, kind: Kind): List[Story] = kind match {
		case Archive => storyFromOr(SelectionV1(doc).many("table #number a").wrapFlat(elementIntoChapterPointer(Page)))
		case Page => storyFromOr(
			if (doc.baseUri() == "http://www.soapylemon.com/") Good(Nil)
			else queryImageInAnchor("body > center > div.mainwindow > center:nth-child(2) > table center img", Page)(doc))
	}
}

