package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.compat.v1.{SelectUtilV1, SelectionV1, Story}
import viscel.compat.v1.Story.More.{Archive, Kind, Page}
import viscel.compat.v1.Story.{Chapter, More}
import SelectUtilV1._
import viscel.narration.NarratorV1

object CitrusSaburoUta extends NarratorV1 {

	def archive = More("http://mangafox.me/manga/citrus_saburo_uta/", Archive) :: Nil

	def id: String = "Mangafox_Citrus"

	def name: String = "CITRUS (SABURO UTA)"

	def wrapArchive(doc: Document): List[Story] Or Every[ErrorMessage] = {
		SelectionV1(doc).many(".chlist li div:has(.tips):has(.title)").reverse.wrapFlat { chapter =>
			val title_? = SelectionV1(chapter).unique(".title").getOne.map(_.ownText())
			val anchorSel = SelectionV1(chapter).unique("a.tips")
			val uri_? = anchorSel.wrapOne { extractUri }
			val text_? = anchorSel.getOne.map { _.ownText() }
			withGood(title_?, uri_?, text_?) { (title, uri, text) =>
				Chapter(s"$text $title") :: More(uri, Page) :: Nil
			}
		}
	}

	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => wrapArchive(doc)
		case Page => queryImageNext("#viewer img", "#top_bar .next_page:not([onclick])", Page)(doc)
	})
}
