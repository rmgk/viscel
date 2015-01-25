package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic._
import viscel.narration.SelectUtil._
import viscel.narration.{Metarrator, Narrator, Selection}
import viscel.shared.Story.More.{Archive, Kind, Page}
import viscel.shared.{Story, ViscelUrl}

import scala.Predef.augmentString

object MangaHere {

	case class Generic(id: String, name: String, archiveUri: ViscelUrl) extends Narrator {
		def archive = Story.More(archiveUri, Archive) :: Nil
		def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
			case Archive => Selection(doc).many(".detail_list > ul:first-of-type a").reverse.wrapFlat { elementIntoChapterPointer(Page) }
			case Page => queryImageNext("#image", ".next_page:not([onclick])", Page)(doc)
		})
	}

	object MetaCore extends Metarrator[Generic]("MangaHere") {

		override def unapply(vurl: ViscelUrl): Option[ViscelUrl] = if (vurl.toString.startsWith("http://www.mangahere.co/manga/")) Some(vurl) else None

		val extractID = """http://www.mangahere.co/manga/([^/]+)/""".r

		override def wrap(doc: Document): List[Generic] Or Every[ErrorMessage] =
			Selection(doc).unique("#main > article > div > div.box_w.clearfix > h1").getOne.map { anchor =>
				val extractID(id) = doc.baseUri()
				Generic(s"MangaHere_$id", s"[MH] ${ anchor.text() }", doc.baseUri()) :: Nil
			}
	}

}
