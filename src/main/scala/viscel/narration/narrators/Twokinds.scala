package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.compat.v1.{SelectionV1, Story}
import viscel.compat.v1.Story.More.{Archive, Issue, Kind, Page}
import viscel.compat.v1.Story.{Chapter, More}
import viscel.narration.SelectUtil._
import viscel.narration.NarratorV1


object Twokinds extends NarratorV1 {

	def archive = More("http://twokinds.keenspot.com/?p=archive", Archive) :: More("http://twokinds.keenspot.com/index.php", Issue) :: Nil

	def id: String = "NX_Twokinds"

	def name: String = "Twokinds"

	def wrapArchive(doc: Document): List[Story] Or Every[ErrorMessage] = {
		SelectionV1(doc).many(".archive .chapter").wrapFlat { chapter =>
			val title_? = SelectionV1(chapter).unique("h4").getOne.map(_.ownText())
			val links_? = SelectionV1(chapter).many("a").wrapEach { elementIntoPointer(Page) }
			withGood(title_?, links_?) { (title, links) =>
				Chapter(title) :: links
			}
		}
	}

	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => wrapArchive(doc)
		case Page => SelectionV1(doc).unique("#cg_img img").wrapEach { imgIntoAsset }
		case Issue => SelectionV1(doc).unique(".comic img[src~=images/\\d+\\.\\w+]").wrapEach { imgIntoAsset }
	})
}
