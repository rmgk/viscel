package viscel.narration.narrators

import io.circe.{Decoder, Encoder}
import org.scalactic._
import viscel.narration.Metarrator
import viscel.narration.Queries._
import viscel.narration.interpretation.NarrationInterpretation.{Constant, Decision, NarratorADT, PolicyDecision, Shuffle, WrapPart}
import viscel.scribe.{Link, Vurl}
import viscel.selection.Selection

case class MangaHereNarrator(id: String, name: String, archiveUri: Vurl)

object MangaHere extends Metarrator[MangaHereNarrator]("MangaHere") {

  val mhWrapper = PolicyDecision(
    volatile = Shuffle.of(queryMixedArchive(".detail_list > ul:first-of-type > li , .detail_list > ul:first-of-type a"))(_.reverse),
    normal = Decision(_.ownerDocument().location().endsWith("featured.html"),
      Constant(Good(Nil)),
      queryImageNext("#image", ".next_page:not([href$=featured.html])")))


  override def toNarrator(nar: MangaHereNarrator): NarratorADT = NarratorADT(nar.id, nar.name, Link(nar.archiveUri) :: Nil, mhWrapper)

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
