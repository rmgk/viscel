package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{Good, One, Bad, ErrorMessage, Every, Or}
import viscel.narration.{Metarrator, Selection, Narrator}
import viscel.narration.SelectUtil._
import viscel.shared.{ViscelUrl, Story}
import viscel.shared.Story.More
import viscel.shared.Story.More.{Archive, Page, Kind, Unused}


object Snafu {

	case class Snar(override val id: String, override val name: String, start: ViscelUrl) extends Narrator {
		def archive = More(start, Archive) :: Nil

		def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
			case Archive => Selection(doc).unique(".pagecontentbox").many("a").wrap { anchors =>
				anchors.reverse.validatedBy(elementIntoPointer(Page))
			}
			case Page => queryImage("img[src~=comics/\\d{6}]")(doc)
		})
	}


	object Meta extends Metarrator[Snar]("Snafu") {

		override def unapply(url: ViscelUrl): Option[ViscelUrl] = {
			if (url.toString.matches("^http://(\\w+.)?snafu-comics.com.*")) Some("http://snafu-comics.com/") else None
		}

		override def wrap(doc: Document): List[Snar] Or Every[ErrorMessage] =
			Selection(doc).many("a[href~=http://\\w+.snafu-comics.com]:has(img[src~=http://www.snafu-comics.com/images/comic][width=40][height=100])").wrapEach { anchor =>
				val name_? = Selection(anchor).first("img").getOne.map(_.attr("alt"))
				val uri = anchor.attr("abs:href")
				val id_? =  extract{val rex"http://($id\w+).snafu-comics" = uri; id}
				withGood(name_?, id_?) { (name, id) =>
					Snar(s"Snafu_$id", s"[SNAFU] $name($id)", s"${uri}/archive.php")
				}
			}

	}

}
