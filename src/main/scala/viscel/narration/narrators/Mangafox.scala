package viscel.narration.narrators

import io.circe.generic.semiauto
import org.jsoup.nodes.Document
import org.scalactic.Accumulation.withGood
import org.scalactic.{Every, Or}
import viscel.narration.Queries._
import viscel.narration.{Contents, Metarrator, Narrator, Queries}
import viscel.scribe.Vurl.fromString
import viscel.scribe.{Chapter, Link, Volatile, Vurl}
import viscel.selection.{Report, Selection}


object Mangafox {

	case class Mfox(override val id: String, override val name: String, url: Vurl) extends Narrator {

		override def archive = Link(url, Volatile) :: Nil

		def wrapArchive(doc: Document): Contents = {
			Selection(doc).many(".chlist li div:has(.tips):has(.title)").reverse.wrapFlat { chapter =>
				val title_? = Selection(chapter).unique(".title").getOne.map(_.ownText())
				val anchorSel = Selection(chapter).unique("a.tips")
				val uri_? = anchorSel.wrapOne {extractURL}
				val text_? = anchorSel.getOne.map {_.ownText()}
				withGood(title_?, uri_?, text_?) { (title, uri, text) =>
					Chapter(s"$text $title") :: Link(uri) :: Nil
				}
			}
		}

		def wrap(doc: Document, more: Link): Contents = more match {
			case Link(_, Volatile, _) => wrapArchive(doc)
			case _ => Queries.queryImageNext("#viewer img", "#top_bar .next_page:not([onclick])")(doc)
		}
	}


	object Meta extends Metarrator[Mfox]("Mangafox", semiauto.deriveDecoder, semiauto.deriveEncoder) {

		override def unapply(description: String): Option[Vurl] = description match {
			case rex"^(${url}http://mangafox.me/manga/[^/]+/)" => Some(Vurl.fromString(url))
			case _ => None
		}
		override def wrap(doc: Document): List[Mfox] Or Every[Report] = {
			val name_? = Selection(doc).unique("#title > h1").getOne.map(_.text())
			val rex"^http://mangafox.me/manga/($id[^/]+)/" = doc.baseUri()
			name_?.map(name => {
				val nname = name.replaceAll("\\s+Manga$", "").replaceAll("\\s+Manhwa$", "")
				Mfox(s"Mangafox_$id", s"[MF] $nname", doc.baseUri()) :: Nil
			})
		}
	}
}
