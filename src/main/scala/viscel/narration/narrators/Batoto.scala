package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.{Data, Templates}
import viscel.scribe.narration.SelectMore._
import viscel.scribe.narration.{Asset, Story, SelectMore, Selection, Volatile, More, Narrator}
import viscel.narration.Queries._
import viscel.scribe.report.Report
import viscel.scribe.report.ReportTools._

import scala.collection.immutable.Set

object Batoto {

	def create(id: String, name: String, start: String) = {
		val wrapPage: Document => List[Story] Or Every[Report] = Selection(_).unique("#comic_page").wrapEach(imgIntoAsset)
		Templates.AP(id, s"[BT] $name", start,
		doc => {
			val pages_? = Selection(doc).first("#page_select").many("option:not([selected=selected])").wrapEach { extractMore }
			val currentPage_? = wrapPage(doc)
			val nextChapter_?  = morePolicy(Volatile, Selection(doc).first(".moderation_bar").optional("a:has(img[title=Next Chapter])").wrap(selectMore))
			val chapter_?  = Selection(doc).first("select[name=chapter_select]").unique("option[selected=selected]").getOne.map(e => Data.Chapter(e.text) :: Nil)
			append(chapter_?, currentPage_?, pages_?, nextChapter_?)
		},
		wrapPage
	)}

	val cores: Set[Narrator] = Set(
		("Batoto_nisekoi", "Nisekoi", "http://bato.to/read/_/20464/nisekoi_by_cxc-scans"),
		("Batoto_sankarea", "Sankarea", "http://bato.to/read/_/2015/sankarea_v1_ch1_by_milky-translation"),
		("Batoto_suzuka", "Suzuka", "http://bato.to/read/_/189133/suzuka_v1_by_anime-waves"),
		("Batoto_princess-lucia", "Princess Lucia", "http://bato.to/read/_/275/princess-lucia_v1_by_red-hawk-scans"),
		("Batoto_half-half", "Half and Half", "http://bato.to/read/_/130232/half-half_ch1_by_red-hawk-scans"),
		("Batoto_love-letter-seo-kouji", "Love Letter (SEO Kouji)", "http://bato.to/read/_/30376/love-letter-seo-kouji_ch1_by_red-hawk-scans"),
		("Batoto_loveplus-rinko-days", "LovePlus - Rinko Days", "http://bato.to/read/_/14567/loveplus-rinko-days_by_red-hawk-scans"),
		("Batoto_ws-doubles", "W's - Doubles", "http://bato.to/read/_/122380/ws-doubles_v1_ch1_by_manga-fiends"),
		("Batoto_cross-over", "Cross Over", "http://bato.to/read/_/87120/cross-over_v1_by_scx-scans")
	).map((create _).tupled)

	//	def getCore(desc: CoreDescription): Core = Generic(id = desc.id, name = desc.name, archiveUri = desc.metadata("start"))
	//
	//	object MetaCore extends Core {
	//		override def id: String = "Meta_MangaHere"
	//		override def name: String = "Metacore MangaHere"
	//		override def archive: List[Description] = Pointer("http://www.mangahere.co/mangalist/", "") :: Nil
	//		override def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(
	//			Selection(doc).many("a.manga_info").wrapEach { anchor =>
	//				val name = anchor.attr("rel")
	//				val uri_? = extractUri(anchor)
	//				val id_? = uri_?.flatMap { uri => Predef.wrapString("""manga/(\w+)/""").r.findFirstMatchIn(uri.toString)
	//					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
	//				}
	//				withGood(uri_?, id_?) { (uri, id) =>
	//					CoreDescription("MangaHere", s"MangaHere_$id", name, Map("start" -> uri.toString))
	//				}
	//			})
	//	}

}
