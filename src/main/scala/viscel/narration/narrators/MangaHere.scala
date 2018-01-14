package viscel.narration.narrators

import io.circe.generic.semiauto
import io.circe.{Decoder, Encoder}
import org.jsoup.nodes.Document
import org.scalactic._
import viscel.narration.Queries._
import viscel.narration.{Metarrator, Templates}
import viscel.scribe.Vurl
import viscel.selection.{Report, Selection}

object MangaHere {

	case class Nar(id: String, name: String, archiveUri: Vurl) extends Templates.ArchivePage(
		archiveUri,
		doc => queryMixedArchive(".detail_list > ul:first-of-type > li , .detail_list > ul:first-of-type a")(doc).map(reverse),
		queryImageNext("#image", ".next_page:not([onclick])")
	)

	object MetaCore extends Metarrator[Nar]("MangaHere") {
		override def reader: Decoder[Nar] = semiauto.deriveDecoder[Nar]
		override def writer: Encoder[Nar] = semiauto.deriveEncoder[Nar]

		override def unapply(vurl: String): Option[Vurl] =
			if (vurl.toString.startsWith("http://www.mangahere.co/manga/")) Some(Vurl.fromString(vurl)) else None

		val extractID = """http://www.mangahere.co/manga/([^/]+)/""".r

		override def wrap(doc: Document): List[Nar] Or Every[Report] =
			Selection(doc).unique("#main > article > div > div.box_w.clearfix > h1").getOne.map { anchor =>
				val extractID(id) = doc.baseUri()
				Nar(s"MangaHere_$id", s"[MH] ${anchor.text()}", doc.baseUri()) :: Nil
			}
	}

}
