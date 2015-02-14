package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.compat.v1.{SelectionV1, Story}
import viscel.compat.v1.Story.More.{Archive, Kind, Page}
import viscel.compat.v1.Story.{Chapter, More}
import viscel.narration.SelectUtilV1._
import viscel.narration.NarratorV1

object Flipside extends NarratorV1 {

	def archive = More("http://flipside.keenspot.com/chapters.php", Archive) :: Nil

	def id: String = "NX_Flipside"

	def name: String = "Flipside"

	def wrapArchive(doc: Document) = {
		SelectionV1(doc).many("td:matches(Chapter|Intermission)").wrapFlat { data =>
			val pages_? = SelectionV1(data).many("a").wrapEach(elementIntoPointer(Page)).map { _.distinct }
			val name_? = if (data.text.contains("Chapter"))
				SelectionV1(data).unique("td:root > div:first-child").getOne.map { _.text() }
			else
				SelectionV1(data).unique("p > b").getOne.map { _.text }

			withGood(pages_?, name_?) { (pages, name) =>
				Chapter(name) :: pages
			}
		}
	}

	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => wrapArchive(doc)
		case Page => SelectionV1(doc).unique("img.ksc").wrapEach(imgIntoAsset)
	})
}
