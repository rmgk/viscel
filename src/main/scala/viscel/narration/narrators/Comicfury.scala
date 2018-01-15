package viscel.narration.narrators

import io.circe.generic.semiauto
import org.jsoup.nodes.Document
import org.scalactic.{Every, Good, Or}
import viscel.narration.Queries.RegexContext
import viscel.narration.{Metarrator, Queries, Templates}
import viscel.scribe.Vurl
import viscel.selection.Report

object Comicfury {
	case class Cfury(cid: String, override val name: String) extends Templates.SimpleForward(
		s"http://$cid.thecomicseries.com/comics/1",
		Queries.queryImageNext("#comicimage", "a[rel=next]")) {
		override val id: String = s"Comicfury_$cid"
	}

	object Meta extends Metarrator[Cfury]("Comicfury", semiauto.deriveDecoder, semiauto.deriveEncoder) {
		override def unapply(description: String): Option[Vurl] = description match {
			case rex"http://($cid[^\.]+)\.thecomicseries.com/" => Some(Vurl.fromString(description))
			case _ => None
		}
		override def wrap(document: Document): Or[List[Cfury], Every[Report]] = {
			val rex"http://($cid[^\.]+)\.thecomicseries.com/" = document.baseUri()
			Good(Cfury(cid, s"[CF] $cid") :: Nil)
		}
	}
}
