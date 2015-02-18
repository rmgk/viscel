package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.{Metarrator, Data, Templates}
import viscel.scribe.narration.SelectMore._
import viscel.scribe.narration.{Asset, Story, SelectMore, Selection, Volatile, More, Narrator}
import viscel.narration.Queries._
import viscel.scribe.report.Report
import viscel.scribe.report.ReportTools._

import scala.collection.immutable.Set

object Batoto {

	case class Btt(cid: String, cname: String, start: String) extends Templates.AP(start,
		queryChapterArchive("#content table.chapters_list tr.lang_English.chapter_row a[href~=http://bato.to/read/]")(_).map(reverse),
		queryImageInAnchor("#comic_page")
	) {
		override def id: String = s"Batoto_$cid"
		override def name: String = s"[BT] $cname"
	}

//	val cores: Set[Narrator] = Set(
//		("Batoto_nisekoi", "Nisekoi", "http://bato.to/read/_/20464/nisekoi_by_cxc-scans"),
//		("Batoto_sankarea", "Sankarea", "http://bato.to/read/_/2015/sankarea_v1_ch1_by_milky-translation"),
//		("Batoto_suzuka", "Suzuka", "http://bato.to/read/_/189133/suzuka_v1_by_anime-waves"),
//		("Batoto_princess-lucia", "Princess Lucia", "http://bato.to/read/_/275/princess-lucia_v1_by_red-hawk-scans"),
//		("Batoto_half-half", "Half and Half", "http://bato.to/read/_/130232/half-half_ch1_by_red-hawk-scans"),
//		("Batoto_love-letter-seo-kouji", "Love Letter (SEO Kouji)", "http://bato.to/read/_/30376/love-letter-seo-kouji_ch1_by_red-hawk-scans"),
//		("Batoto_loveplus-rinko-days", "LovePlus - Rinko Days", "http://bato.to/read/_/14567/loveplus-rinko-days_by_red-hawk-scans"),
//		("Batoto_ws-doubles", "W's - Doubles", "http://bato.to/read/_/122380/ws-doubles_v1_ch1_by_manga-fiends"),
//		("Batoto_cross-over", "Cross Over", "http://bato.to/read/_/87120/cross-over_v1_by_scx-scans")
//	).map((Btt.apply _).tupled)


		object Meta extends Metarrator[Btt]("Batoto") {
			override def unapply(description: String): Option[URL] = description match {
				case rex"^http://bato.to/comic/_/comics/" => Some(stringToURL(description))
				case _ => None
			}
			override def wrap(document: Document): Or[List[Btt], Every[Report]] = {
				val rex"^http://bato.to/comic/_/comics/($id[^/]+)" = document.baseUri()
				Selection(document).unique("#content h1.ipsType_pagetitle").getOne.map(e => Btt(id, e.text(), document.baseUri()) :: Nil)
			}
		}

}
