package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic._
import viscel.compat.v1.Story.More.{Archive, Kind, Page}
import viscel.compat.v1.{NarratorV1, SelectUtilV1, SelectionV1, Story, ViscelUrl}
import SelectUtilV1._
import viscel.narration.Metarrator

import scala.Predef.augmentString

object MangaHere {

	case class Generic(id: String, name: String, archiveUri: ViscelUrl) extends NarratorV1 {
		def archive = Story.More(archiveUri, Archive) :: Nil
		def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
			case Archive => SelectionV1(doc).many(".detail_list > ul:first-of-type a").reverse.wrapFlat { elementIntoChapterPointer(Page) }
			case Page => queryImageNext("#image", ".next_page:not([onclick])", Page)(doc)
		})
	}

	object MetaCore extends Metarrator[Generic]("MangaHere") {

		override def unapply(vurl: String): Option[URL] = if (vurl.toString.startsWith("http://www.mangahere.co/manga/")) Some(new URL(vurl)) else None

		val extractID = """http://www.mangahere.co/manga/([^/]+)/""".r

		override def wrap(doc: Document): List[Generic] Or Every[ErrorMessage] =
			SelectionV1(doc).unique("#main > article > div > div.box_w.clearfix > h1").getOne.map { anchor =>
				val extractID(id) = doc.baseUri()
				Generic(s"MangaHere_$id", s"[MH] ${ anchor.text() }", doc.baseUri()) :: Nil
			}
	}

}
