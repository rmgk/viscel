package viscel.narration.narrators

import io.circe.{Decoder, Encoder}
import viscel.narration.Queries._
import viscel.narration.interpretation.NarrationInterpretation
import viscel.narration.interpretation.NarrationInterpretation._
import viscel.narration.{Metarrator, Templates}
import viscel.selection.ReportTools.extract
import viscel.selection.Selection
import viscel.store.Vurl

case class WebToon(id: String, name: String, start: Vurl)
object WebToons extends Metarrator[WebToon]("WebToons") {

  override def toNarrator(wt: WebToon): NarratorADT =
    Templates.SimpleForward("WebToons_" + wt.id, wt.name, wt.start,
                            Append(Selection.many("#_imageList img")
                                   .wrapEach(imageFromAttribute(_, Some("data-url"))),
                                   queryNext("a.pg_next[title=Next Episode]")))
  override def decoder: Decoder[WebToon] = io.circe.generic.semiauto.deriveDecoder
  override def encoder: Encoder[WebToon] = io.circe.generic.semiauto.deriveEncoder
  override def unapply(description: String): Option[Vurl] = description match {
    case rex"https://www.webtoons.com/[^/]+/[^/]+/($cid[^/]+)/.*title_no=($number\d+)" =>
      Some(Vurl.fromString(description))
    case _ => None
  }
  override def wrap: NarrationInterpretation.WrapPart[List[WebToon]] = {
    val url_? = Selection.unique("#_btnEpisode").wrapOne(extractURL)
    val name_? = Selection.unique("#content .detail_header .info .subj").wrapOne(e => extract(e.ownText()))
    Combination.of(url_?, name_?){(url, name) =>
      val rex"https://www.webtoons.com/[^/]+/[^/]+/($cid[^/]+)/.*" = url.uriString()
      List(WebToon(cid, name, url))
    }
  }
}
