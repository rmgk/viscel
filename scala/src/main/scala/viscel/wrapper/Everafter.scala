package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import viscel.core._
import viscel.description.{Chapter, Structure, Pointer, Description}

object Everafter extends Core with WrapperTools with StrictLogging {
	def archive = Chapter("") :: Pointer("http://ea.snafu-comics.com/archive.php", "archive")

		Structure( next = Pointer("http://ea.snafu-comics.com/archive.php", "archive"),
		payload = Chapter("No Chapters?!"))

	def id: String = "Snafu_Everafter"

	def name: String = "Everafter"

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => Selection(doc).unique(".pagecontentbox").all("a").wrap { anchors => anchorsIntoPointers("page")(anchors.reverse) }
		case "page" => Selection(doc).unique("img[src~=comics/\\d{6}]").wrapOne { imgIntoStructure }
	})
}
