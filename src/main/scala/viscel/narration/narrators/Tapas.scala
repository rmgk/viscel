package viscel.narration.narrators

import io.circe.{Decoder, Encoder}
import viscel.narration.Queries._
import viscel.narration.{Metarrator, NarratorADT, Templates}
import viscel.selektiv.Narration._
import viscel.selektiv.ReportTools.extract
import viscel.selektiv.{Narration, Selection}
import viscel.store.v4.Vurl

case class Tapas(id: String, name: String, start: Vurl)
object Tapas extends Metarrator[Tapas]("Tapas") {

  override def toNarrator(wt: Tapas): NarratorADT =
    Templates.SimpleForward("Tapas_" + wt.id, wt.name, wt.start,
                            Append(Selection.many("img.content__img")
                                            .wrapEach(imageFromAttribute(_, Some("data-src"))),
                                   Selection.all("a.tab__button--small.js-next-ep-btn")
                                            .wrapEach(extractMore)))

  override def decoder: Decoder[Tapas] = io.circe.generic.semiauto.deriveDecoder
  override def encoder: Encoder[Tapas] = io.circe.generic.semiauto.deriveEncoder
  override def unapply(description: String): Option[Vurl] = description match {
    case rex"https://tapas.io/series/($name\w+)" =>
      Some(Vurl.fromString(description))
    case _                                       => None
  }

  override def wrap: Narration.WrapPart[List[Tapas]] = {
    val url_?   = Selection.unique(".header .additional__btn--quince").wrapOne(extractURL)
    val name_?  = Selection.unique(".info__desc .desc__title").wrapOne(e => extract(e.ownText()))
    val canon_? = Selection.unique("link[rel=canonical]").wrapOne(extractURL)

    val cn_? = Combination.of(canon_?, name_?) { (canon, name) =>
      val rex"https://tapas.io/series/($cid\w+)" = canon.uriString()
      (cid, name)
    }

    Combination.of(cn_?, url_?) { (cidname, url) =>
      List(Tapas(cidname._1, cidname._2, url))
    }
  }
}
