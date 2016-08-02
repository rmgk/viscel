package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import upickle.default
import viscel.narration.Queries._
import viscel.narration.{Metarrator, Templates}
import viscel.scribe.{Article, Vurl}
import viscel.selection.ReportTools._
import viscel.selection.{Report, Selection}


object KissManga {

	case class Kss(cid: String, cname: String, start: String) extends Templates.AP(start,
		queryChapterArchive("#leftside div.chapterList table.listing a[href~=^/Manga/.*")(_).map(reverse),
		doc => {
			val html = doc.html()
			val re = """lstImages.push\("([^"]+)"\);""".r
			extract {
				re.findAllIn(html).matchData.map { md =>
					val url = md.subgroups(0)
					Article(url, doc.baseUri())
				}.toList
			}
		}
	) {
		override def id: String = s"KissManga_$cid"
		override def name: String = s"[KM] $cname"
	}

	object Meta extends Metarrator[Kss]("KissManga") {
		override def reader: default.Reader[Kss] = implicitly[default.Reader[Kss]]
		override def writer: default.Writer[Kss] = implicitly[default.Writer[Kss]]

		override def unapply(description: String): Option[Vurl] = description match {
			case rex"^http://kissmanga.com/Manga/.*" => Some(Vurl.fromString(description))
			case _ => None
		}

		override def wrap(document: Document): Or[List[Kss], Every[Report]] = {
			val rex"^http://kissmanga.com/Manga/($id[^/]+)" = document.baseUri()
			Selection(document).unique("#leftside a.bigChar").getOne.map(e => Kss(java.net.URLDecoder.decode(id, "UTF-8"), e.text(), document.baseUri()) :: Nil)
		}
	}

}
