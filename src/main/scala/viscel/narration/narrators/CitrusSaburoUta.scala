package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.{Chapter, More}

import scala.Predef.$conforms

object CitrusSaburoUta extends Narrator {

	def archive = More("http://mangafox.me/manga/citrus_saburo_uta/", "archive") :: Nil

	def id: String = "Mangafox_Citrus"

	def name: String = "CITRUS (SABURO UTA)"

	def wrapArchive(doc: Document): List[Story] Or Every[ErrorMessage] = {
		Selection(doc).many(".chlist li div :has(.tips):has(.title)").reverse.wrapEach { chapter =>
			val title_? = Selection(chapter).unique(".title").getOne.map(_.ownText())
			val anchor_? = Selection(chapter).unique("a.tips")
			val uri_? = anchor_?.wrapOne { extractUri }
			val text_? = anchor_?.getOne.map { _.ownText() }
			withGood(title_?, uri_?, text_?) { (title, uri, text) =>
				Chapter(s"$text $title") :: More(uri, "page") :: Nil
			}
		}.map(_.flatten)
	}

	def wrapPage(doc: Document) = {
		val next_? = Selection(doc).optional("#top_bar .next_page:not([onclick])").wrap { selectNext("page") }
		val img_? = Selection(doc).unique("#viewer img").wrapEach(imgIntoAsset)
		withGood(img_?, next_?) { _ ::: _ }
	}

	def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
		case "archive" => wrapArchive(doc)
		case "page" => wrapPage(doc)
	})
}
