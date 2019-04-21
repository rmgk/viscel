package viscel.narration.narrators

import io.circe.generic.semiauto
import io.circe.{Decoder, Encoder}
import viscel.narration.Queries.RegexContext
import viscel.narration.{Metarrator, NarratorADT, Queries, Templates}
import viscel.netzi.Vurl
import viscel.netzi.Narration.{ElementW, WrapPart}

case class Cfury(id: String, name: String)

object Comicfury extends Metarrator[Cfury]("Comicfury") {

  override def toNarrator(cf: Cfury): NarratorADT =
    Templates.SimpleForward(s"Comicfury_${cf.id}", cf.name,
                            s"http://${cf.id}.thecomicseries.com/comics/1",
                            Queries.queryImageNext("#comicimage", "a[rel=next]"))

  override def decoder: Decoder[Cfury] = semiauto.deriveDecoder
  override def encoder: Encoder[Cfury] = semiauto.deriveEncoder

  override def unapply(description: String): Option[Vurl] = description match {
    case rex"http://($cid[^\.]+)\.thecomicseries.com/" => Some(Vurl.fromString(description))
    case _ => None
  }

  override def wrap: WrapPart[List[Cfury]] = ElementW.map { document =>
    val rex"http://($cid[^\.]+)\.thecomicseries.com/" = document.baseUri()
    Cfury(cid, s"[CF] $cid") :: Nil
  }

}
