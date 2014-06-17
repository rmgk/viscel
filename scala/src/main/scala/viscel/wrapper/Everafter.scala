package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.core._
import viscel.description._
import viscel.wrapper.Util._

object Everafter extends Core with StrictLogging {
	def archive = Chapter("") :: Pointer("http://ea.snafu-comics.com/archive.php", "archive")

	def id: String = "Snafu_Everafter"

	def name: String = "Everafter"

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => Selection(doc).unique(".pagecontentbox").many("a").wrap { anchors =>
			anchors.reverse.validatedBy(anchorIntoPointer("page")).map { pointer => Structure(children = pointer) }
		}
		case "page" => Selection(doc).unique("img[src~=comics/\\d{6}]").wrapOne { imgIntoStructure }
	})
}
