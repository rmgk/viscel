package viscel.narration.narrators

import io.circe.{Decoder, Encoder}
import org.scalactic._
import viscel.narration.Queries._
import viscel.narration.{Metarrator, NarratorADT, Templates}
import viscel.netzi.Vurl
import viscel.selektiv.Narration.{Constant, Decision, MapW, WrapPart}
import viscel.selektiv.Selection

case class MangaHereNarrator(id: String, name: String, archiveUri: Vurl)

// currently borked
object MangaHere extends Metarrator[MangaHereNarrator]("MangaHere") {

  val archiveWrapper = MapW(queryMixedArchive(".detail_list > ul:first-of-type > li , .detail_list > ul:first-of-type a"), chapterReverse)
  val pageWrapper = Decision(_.ownerDocument().location().endsWith("featured.html"),
      Constant(Nil),
      queryImageNext("#image", ".next_page:not([href$=featured.html])"))


  override def toNarrator(nar: MangaHereNarrator): NarratorADT =
    Templates.archivePage(nar.id, nar.name, nar.archiveUri, archiveWrapper, pageWrapper)

  import viscel.store.v4.V4Codecs.{uriReader, uriWriter}

  val decoder = Decoder.forProduct3("id", "name", "archiveUri")((i, n, a) => MangaHereNarrator(i, n, a))
  val encoder: Encoder[MangaHereNarrator] = Encoder.forProduct3("id", "name", "archiveUri")(nar => (nar.id, nar.name, nar.archiveUri))

  override def unapply(vurl: String): Option[Vurl] =
    if (vurl.toString.startsWith("http://www.mangahere.co/manga/")) Some(Vurl.fromString(vurl)) else None

  val extractID = """http://www.mangahere.co/manga/([^/]+)/""".r

  override val wrap: WrapPart[List[MangaHereNarrator]] =
    Selection.unique("#main > article > div > div.box_w.clearfix > h1").wrapOne { anchor =>
      val extractID(id) = anchor.ownerDocument().baseUri()
      Good(MangaHereNarrator(s"MangaHere_$id", s"[MH] ${anchor.text()}", anchor.ownerDocument().baseUri()) :: Nil)
    }

}
