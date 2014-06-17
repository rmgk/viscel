package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.core._
import viscel.description._
import viscel.wrapper.Util._

object CitrusSaburoUta extends Core with StrictLogging {

	def archive = Pointer("http://mangafox.me/manga/citrus_saburo_uta/", "archive")

	def id: String = "Mangafox_Citrus"

	def name: String = "CITRUS (SABURO UTA)"

	def wrapArchive(doc: Document) = {
		Selection(doc).many(".chlist li div :has(.tips):has(.title)").reverse.wrapEach { chapter =>
			val title_? = Selection(chapter).unique(".title").getOne.map(_.ownText())
			val anchor_? = Selection(chapter).unique("a.tips")
			val uri_? = anchor_?.wrapOne { extractUri }
			val text_? = anchor_?.getOne.map { _.ownText() }
			withGood(title_?, uri_?, text_?) { (title, uri, text) =>
				Chapter(s"$text $title") :: Pointer(uri, "page")
			}
		}.map { chapters => Structure(children = chapters) }
	}

	def wrapPage(doc: Document) = {
		val next_? = Selection(doc).optional("#top_bar .next_page:not([onclick])").wrap { selectNext("page") }
		val img_? = Selection(doc).unique("#viewer img").wrapOne(imgIntoStructure)
		withGood(img_?, next_?) { _ :: _ }
	}

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc)
		case "page" => wrapPage(doc)
	})
}
