package viscel.narration.narrators

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import viscel.narration.{Metarrator, Narrator, Templates}
import viscel.selektiv.Narration.*
import viscel.selektiv.Queries.*
import viscel.selektiv.ReportTools.extract
import viscel.selektiv.{Narration, Selection}
import viscel.shared.{DataRow, Vurl}

case class Tapas(id: String, name: String, start: Vurl)
object Tapas extends Metarrator[Tapas]("Tapas") {

  override def toNarrator(wt: Tapas): Narrator =
    Templates.SimpleForward(
      "Tapas_" + wt.id,
      wt.name,
      wt.start,
      Append(
        Selection.all("img.content__img")
          .map(_.map(imageFromAttribute(_, Some("data-src")))),
        Selection.all("div.episode-unit.js-episode-wrap")
          .map(_.flatMap { elem =>
            val id = extract { elem.attr("data-next-id") }
            if (id == "-1") None
            else Some(DataRow.Link(Vurl.fromString(s"https://tapas.io/episode/$id")))
          })
      )
    )

  override def codec: JsonValueCodec[Tapas] = {
    import viscel.shared.JsoniterCodecs.*
    JsonCodecMaker.make
  }

  override def unapply(description: String): Option[Vurl] =
    description match {
      case rex"https://tapas.io/series/($cid[^/]+)(?:/.*)?" =>
        Some(Vurl.fromString(s"https://tapas.io/series/$cid/info"))
      case _ => None
    }

  override def wrap: Narration.WrapPart[List[Tapas]] = {
    val url_?   = Selection.unique(".info__right .button.button--read.js-continue-btn").map(extractURL)
    val name_?  = Selection.unique(".title-wrapper .title").map(e => extract(e.ownText()))
    val canon_? = Selection.unique("link[rel=canonical]").map(extractURL)

    val cn_? = Combination.of(canon_?, name_?) { (canon, name) =>
      val rex"https://tapas.io/series/($cid[^/]+)/info" = canon.uriString() : @unchecked
      (cid, name)
    }

    Combination.of(cn_?, url_?) { (cidname, url) =>
      List(Tapas(cidname._1, cidname._2, url))
    }
  }
}
