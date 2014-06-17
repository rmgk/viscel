package viscel.wrapper

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.core.Core
import viscel.description.{Description, Structure, Chapter, Pointer}
import viscel.wrapper.Util._


object OldBoy extends Core {

	def archive = Pointer("http://www.mangahere.co/manga/old_boy/", "archive")

	def id: String = "Mangahere_OldBoy"

	def name: String = "Old Boy"

	def wrapArchive(doc: Document) = {
		Selection(doc).many(".detail_list > ul:first-of-type a").reverse.wrapEach { chapter =>
			val pointer_? = anchorIntoPointer("page")(chapter)
			withGood(pointer_?) { (pointer) =>
				Chapter(chapter.text()) :: pointer
			}
		}.map { chapters => Structure(children = chapters) }
	}

	def wrapPage(doc: Document) = {
		val next_? = Selection(doc).optional(".next_page:not([onclick])").wrap { selectNext("page") }
		val img_? = Selection(doc).unique("#image").wrapOne(imgIntoStructure)
		withGood(img_?, next_?) { _ :: _ }
	}

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc)
		case "page" => wrapPage(doc)
	})
}

