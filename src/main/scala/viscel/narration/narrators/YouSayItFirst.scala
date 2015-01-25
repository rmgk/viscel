package viscel.narration.narrators

import org.jsoup.nodes.Document
import viscel.narration.SelectUtil.{elementIntoChapterPointer, queryImageInAnchor, storyFromOr, stringToVurl}
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More
import viscel.shared.Story.More.{Archive, Kind, Page}

object YouSayItFirst extends Narrator {
	override def id: String = "NX_YouSayItFirst"
	override def name: String = "You Say It First"
	override def archive: List[Story] = Range.inclusive(1, 9).map(i => More(s"http://www.yousayitfirst.com/archive/index.php?year=$i", Archive)).toList
	override def wrap(doc: Document, kind: Kind): List[Story] = kind match {
		case Archive => storyFromOr(Selection(doc).many("table #number a").wrapFlat(elementIntoChapterPointer(Page)))
		case Page => storyFromOr(queryImageInAnchor("body > center > div.mainwindow > center:nth-child(2) > table center img", Page)(doc))
	}
}

