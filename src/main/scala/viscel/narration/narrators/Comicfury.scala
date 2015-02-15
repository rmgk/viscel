package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{Good, Every, Or}
import viscel.narration.{Metarrator, Queries, Templates}
import viscel.scribe.report.Report
import viscel.narration.Queries.RegexContext
import viscel.scribe.narration.SelectMore.stringToURL

object Comicfury {
	case class Cfury(cid: String, override val name: String) extends Templates.SF(
		s"http://$cid.thecomicseries.com/comics/1",
		Queries.queryImageNext("#comicimage", "a[rel=next]")) {
		override val id: String = s"Comicfury_$cid"
	}

	object Meta extends Metarrator[Cfury]("Comicfury") {
		override def unapply(description: String): Option[URL] = description match {
			case rex"http://($cid[^\.]+)\.thecomicseries.com/" => Some(description)
			case _ => None
		}
		override def wrap(document: Document): Or[List[Cfury], Every[Report]] ={
			val rex"http://($cid[^\.]+)\.thecomicseries.com/" = document.baseUri()
			Good(Cfury(cid, s"[CF] $cid") :: Nil)
		}
	}
}
