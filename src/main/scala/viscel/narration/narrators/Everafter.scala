package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More
import viscel.shared.Story.More.{Page, Archive, Kind}

object Everafter extends Narrator {
	def archive = More("http://ea.snafu-comics.com/archive.php", Archive) :: Nil

	def id: String = "Snafu_Everafter"

	def name: String = "Everafter"

	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => Selection(doc).unique(".pagecontentbox").many("a").wrap { anchors =>
			anchors.reverse.validatedBy(elementIntoPointer(Page))
		}
		case Page => Selection(doc).unique("img[src~=comics/\\d{6}]").wrapEach(imgIntoAsset)
	})
}
