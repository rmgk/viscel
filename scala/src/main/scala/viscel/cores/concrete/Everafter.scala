package viscel.cores.concrete

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.cores.Util._
import viscel.cores.{Core, Selection}
import viscel.description.Description
import viscel.description.Description.Pointer

object Everafter extends Core with StrictLogging {
	def archive = Pointer("http://ea.snafu-comics.com/archive.php", "archive") :: Nil

	def id: String = "Snafu_Everafter"

	def name: String = "Everafter"

	def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(pd.pagetype match {
		case "archive" => Selection(doc).unique(".pagecontentbox").many("a").wrap { anchors =>
			anchors.reverse.validatedBy(elementIntoPointer("page"))
		}
		case "page" => Selection(doc).unique("img[src~=comics/\\d{6}]").wrapEach(imgIntoAsset)
	})
}
