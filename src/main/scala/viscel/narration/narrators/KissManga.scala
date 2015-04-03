package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import viscel.narration.Queries._
import viscel.narration.SelectMore._
import viscel.narration.{Data, Metarrator, SelectMore, Templates}
import viscel.selection.ReportTools._
import viscel.selection.{Report, Selection}

import scala.Predef.augmentString


object KissManga {

	case class Kss(cid: String, cname: String, start: String) extends Templates.AP(start,
		queryChapterArchive("#leftside div.chapterList table.listing a[href~=^/Manga/.*")(_).map(reverse),
		doc => {
			val html = doc.html()
			val re = """lstImages.push\("([^"]+)"\);""".r
			extract {
				re.findAllIn(html).matchData.map { md =>
					val url = md.subgroups(0)
					Data.Article(url, doc.baseUri())
				}.toList
			}
		}
	) {
		override def id: String = s"KissManga_$cid"
		override def name: String = s"[KM] $cname"
	}

	object Meta extends Metarrator[Kss]("KissManga") {
		override def unapply(description: String): Option[URL] = description match {
			case rex"^http://kissmanga.com/Manga/.*" => Some(stringToURL(description))
			case _ => None
		}

		override def wrap(document: Document): Or[List[Kss], Every[Report]] = {
			val rex"^http://kissmanga.com/Manga/($id[^/]+)" = document.baseUri()
			Selection(document).unique("#leftside a.bigChar").getOne.map(e => Kss(java.net.URLDecoder.decode(id, "UTF-8"), e.text(), document.baseUri()) :: Nil)
		}
	}

}
