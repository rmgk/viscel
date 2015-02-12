package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More.{Archive, Kind, Page}
import viscel.shared.Story.{Chapter, More}

object Flipside extends Narrator {

	def archive = More("http://flipside.keenspot.com/chapters.php", Archive) :: Nil

	def id: String = "NX_Flipside"

	def name: String = "Flipside"

	def wrapArchive(doc: Document) = {
		Selection(doc).many("td:matches(Chapter|Intermission)").wrapFlat { data =>
			val pages_? = Selection(data).many("a").wrapEach(elementIntoPointer(Page)).map { _.distinct }
			val name_? = if (data.text.contains("Chapter"))
				Selection(data).unique("td:root > div:first-child").getOne.map { _.text() }
			else
				Selection(data).unique("p > b").getOne.map { _.text }

			withGood(pages_?, name_?) { (pages, name) =>
				Chapter(name) :: pages
			}
		}
	}

	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => wrapArchive(doc)
		case Page => Selection(doc).unique("img.ksc").wrapEach(imgIntoAsset)
	})
}
