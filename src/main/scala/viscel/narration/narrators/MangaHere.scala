package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.narration.SelectUtil._
import viscel.narration.{Metarrator, Narrator, Selection}
import viscel.shared.Story.More.{Archive, Page, Kind}
import viscel.shared.{Story, ViscelUrl}

object MangaHere {

	case class Generic(id: String, name: String, archiveUri: ViscelUrl) extends Narrator {
		def archive = Story.More(archiveUri, Archive) :: Nil
		def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
			case Archive => Selection(doc).many(".detail_list > ul:first-of-type a").reverse.wrapFlat { elementIntoChapterPointer(Page) }
			case Page => queryImageNext("#image", ".next_page:not([onclick])", Page)(doc)
		})
	}

	object MetaCore extends Metarrator[Generic]("MangaHere") {
		override def archive: ViscelUrl = vurlToString("http://www.mangahere.co/mangalist/")
		override def wrap(doc: Document): List[Generic] Or Every[ErrorMessage] =
			Selection(doc).many("a.manga_info").wrapEach { anchor =>
				val name = anchor.attr("rel")
				val uri_? = extractUri(anchor)
				val id_? = uri_?.flatMap { uri => Predef.wrapString( """manga/(\w+)/""").r.findFirstMatchIn(uri.toString)
					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
				}
				withGood(uri_?, id_?) { (uri, id) =>
					Generic(s"MangaHere_$id", s"[MH] $name", uri)
				}
			}
	}

}
