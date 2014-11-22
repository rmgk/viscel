package viscel.narration.narrators

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More

object Everafter extends Narrator with StrictLogging {
	def archive = More("http://ea.snafu-comics.com/archive.php", "archive") :: Nil

	def id: String = "Snafu_Everafter"

	def name: String = "Everafter"

	def wrap(doc: Document, pd: More): List[Story] = storyFromOr(pd.pagetype match {
		case "archive" => Selection(doc).unique(".pagecontentbox").many("a").wrap { anchors =>
			anchors.reverse.validatedBy(elementIntoPointer("page"))
		}
		case "page" => Selection(doc).unique("img[src~=comics/\\d{6}]").wrapEach(imgIntoAsset)
	})
}
