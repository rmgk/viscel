package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic._
import viscel.narration.Queries._
import viscel.narration.SelectMore.stringToURL
import viscel.narration.{Metarrator, Templates}
import viscel.selection.{Report, Selection}
import viscel.store.Json.{urlReader, urlWriter}

import scala.Predef.augmentString

object MangaHere {
	(urlReader, urlWriter) // reference so that optimize imports does not remove the import

	case class Nar(id: String, name: String, archiveUri: URL) extends Templates.AP(
		archiveUri,
		Selection(_).many(".detail_list > ul:first-of-type a").reverse.wrapFlat { elementIntoChapterPointer },
		queryImageNext("#image", ".next_page:not([onclick])")
	)

	object MetaCore extends Metarrator[Nar]("MangaHere") {

		override def unapply(vurl: String): Option[URL] = if (vurl.toString.startsWith("http://www.mangahere.co/manga/")) Some(new URL(vurl)) else None

		val extractID = """http://www.mangahere.co/manga/([^/]+)/""".r

		override def wrap(doc: Document): List[Nar] Or Every[Report] =
			Selection(doc).unique("#main > article > div > div.box_w.clearfix > h1").getOne.map { anchor =>
				val extractID(id) = doc.baseUri()
				Nar(s"MangaHere_$id", s"[MH] ${ anchor.text() }", doc.baseUri()) :: Nil
			}
	}

}
