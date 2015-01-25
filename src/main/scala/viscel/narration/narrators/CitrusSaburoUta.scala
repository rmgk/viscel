package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More.{Archive, Kind, Page}
import viscel.shared.Story.{Chapter, More}

object CitrusSaburoUta extends Narrator {

	def archive = More("http://mangafox.me/manga/citrus_saburo_uta/", Archive) :: Nil

	def id: String = "Mangafox_Citrus"

	def name: String = "CITRUS (SABURO UTA)"

	def wrapArchive(doc: Document): List[Story] Or Every[ErrorMessage] = {
		Selection(doc).many(".chlist li div:has(.tips):has(.title)").reverse.wrapFlat { chapter =>
			val title_? = Selection(chapter).unique(".title").getOne.map(_.ownText())
			val anchorSel = Selection(chapter).unique("a.tips")
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
