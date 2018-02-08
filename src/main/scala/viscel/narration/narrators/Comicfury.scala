package viscel.narration.narrators

import io.circe.generic.semiauto
import io.circe.{Decoder, Encoder}
import org.scalactic.Good
import viscel.narration.Queries.RegexContext
import viscel.narration.interpretation.NarrationInterpretation
import viscel.narration.interpretation.NarrationInterpretation.{DocumentWrapper, WrapPart}
import viscel.narration.{Metarrator, Queries, Templates}
import viscel.scribe.Vurl

case class Cfury(id: String, name: String)

object Comicfury extends Metarrator[Cfury]("Comicfury") {

  override def toNarrator(cf: Cfury): NarrationInterpretation.NarratorADT = Templates.SimpleForward(s"Comicfury_${cf.id}", cf.name,
    s"http://${cf.id}.thecomicseries.com/comics/1",
    Queries.queryImageNext("#comicimage", "a[rel=next]"))

  override def decoder: Decoder[Cfury] = semiauto.deriveDecoder
  override def encoder: Encoder[Cfury] = semiauto.deriveEncoder

  override def unapply(description: String): Option[Vurl] = description match {
    case rex"http://($cid[^\.]+)\.thecomicseries.com/" => Some(Vurl.fromString(description))
    case _ => None
  }

  override def wrap: WrapPart[List[Cfury]] = DocumentWrapper { document =>
    val rex"http://($cid[^\.]+)\.thecomicseries.com/" = document.baseUri()
    Good(Cfury(cid, s"[CF] $cid") :: Nil)
  }

}
