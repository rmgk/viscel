package viscel.narration.narrators

import io.circe.{Decoder, Encoder}
import org.jsoup.nodes.Document
import org.scalactic._
import viscel.narration.Metarrator
import viscel.narration.Queries._
import viscel.narration.interpretation.NarrationInterpretation.{DocumentWrapper, NarratorADT, PolicyDecision}
import viscel.scribe.{Link, Vurl}
import viscel.selection.{Report, Selection}

case class MangaHereNarrator(id: String, name: String, archiveUri: Vurl)

object MangaHere extends Metarrator[MangaHereNarrator]("MangaHere") {



  val mhWrapper = PolicyDecision(
    volatile = DocumentWrapper(doc =>
      queryMixedArchive(".detail_list > ul:first-of-type > li , .detail_list > ul:first-of-type a")(doc).map(reverse)),
    normal = DocumentWrapper(doc =>
      if (doc.location().endsWith("featured.html")) Good(Nil) else queryImageNext("#image", ".next_page:not([href$=featured.html])")(doc)))


  override def toNarrator(nar: MangaHereNarrator): NarratorADT = NarratorADT(nar.id, nar.name, Link(nar.archiveUri) :: Nil, mhWrapper)

  val decoder = Decoder.forProduct3("id", "name", "archiveUri")((i, n, a) => MangaHereNarrator(i, n, a))
  val encoder: Encoder[MangaHereNarrator] = Encoder.forProduct3("id", "name", "archiveUri")(nar => (nar.id, nar.name, nar.archiveUri))

  override def unapply(vurl: String): Option[Vurl] =
    if (vurl.toString.startsWith("http://www.mangahere.co/manga/")) Some(Vurl.fromString(vurl)) else None

  val extractID = """http://www.mangahere.co/manga/([^/]+)/""".r

  override def wrap(doc: Document): List[MangaHereNarrator] Or Every[Report] =
    Selection(doc).unique("#main > article > div > div.box_w.clearfix > h1").getOne.map { anchor =>
      val extractID(id) = doc.baseUri()
      MangaHereNarrator(s"MangaHere_$id", s"[MH] ${anchor.text()}", doc.baseUri()) :: Nil
    }

}
