package viscel.narration.narrators

import io.circe.{Decoder, Encoder}
import viscel.selektiv.Queries._
import viscel.narration.{Metarrator, Narrator, Templates}
import viscel.selektiv.ReportTools.extract
import viscel.selektiv.Narration._
import viscel.selektiv.{Narration, Selection}
import viscel.shared.Vurl
import viscel.shared.CirceCodecs.{vurlReader, vurlWriter}


case class WebToon(id: String, name: String, start: Vurl)
object WebToons extends Metarrator[WebToon]("WebToons") {

  override def toNarrator(wt: WebToon): Narrator =
    Templates.SimpleForward("WebToons_" + wt.id, wt.name, wt.start,
                            Append(Selection.many("#_imageList img")
                                   .map(_.map(imageFromAttribute(_, Some("data-url")))),
                                   queryNext("a.pg_next[title=Next Episode]")))
  override def decoder: Decoder[WebToon] = io.circe.generic.semiauto.deriveDecoder
  override def encoder: Encoder[WebToon] = io.circe.generic.semiauto.deriveEncoder
  override def unapply(description: String): Option[Vurl] = description match {
    case rex"https://www.webtoons.com/[^/]+/[^/]+/($cid[^/]+)/.*title_no=($number\d+)" =>
      Some(Vurl.fromString(description))
    case _ => None
  }
  override def wrap: Narration.WrapPart[List[WebToon]] = {
    val url_? = Selection.unique("#_btnEpisode").map(extractURL)
    val name_? = Selection.unique("#content .detail_header .info .subj").map(e => extract(e.ownText()))
    Combination.of(url_?, name_?){(url, name) =>
      val rex"https://www.webtoons.com/[^/]+/[^/]+/($cid[^/]+)/.*" = url.uriString()
      List(WebToon(cid, name, url))
    }
  }
}
