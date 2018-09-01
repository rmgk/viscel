package viscel.narration.narrators

import io.circe.{Decoder, DecodingFailure, Encoder}
import org.scalactic.Accumulation._
import org.scalactic.{Every, Good, One, Or}
import viscel.narration.interpretation.NarrationInterpretation.{Combination, JsonWrapper, Location, NarratorADT, WrapPart, Wrapper}
import viscel.narration.{Metarrator, Templates}
import viscel.selection.JsonDecoding
import viscel.selection.ReportTools.{EitherOps, extract}
import viscel.store.{Chapter, ImageRef, Link, Vurl, WebContent}

import scala.util.Try

case class MangadexNarrator(id: String, name: String, archiveUri: Vurl)

object Mangadex extends Metarrator[MangadexNarrator]("Mangadex") {

  case class ChapterInfo(volume: Option[Int], num: Int, numStr: String, title: String, id: String) {
    def sorting: (Int, Int, String, String, String) = (volume.getOrElse(Int.MaxValue), num, numStr, title, id)
    def contents: List[WebContent] =
      Chapter(s"(${volume.fold("")(i => s"$i: ")}$num) $title") ::
      Link(Vurl.fromString(s"https://mangadex.org/api/?id=$id&type=chapter")) ::
      Nil
  }

  val archiveWrapper: Wrapper = {
    JsonWrapper { json =>
      val chaptersMap = json.hcursor.downField("chapter")
      chaptersMap.keys.get.filter(cid => chaptersMap.downField(cid).get[String]("lang_code").getOrElse("") == "gb")
      .map { cid =>
        val chap = chaptersMap.downField(cid)
        val chapname: Or[String, One[DecodingFailure]] = chap.get[String]("chapter").ors
        val volume = chap.get[String]("volume").toOption.flatMap(v => Try(v.toInt).toOption)
        val num: Or[Int, Every[DecodingFailure]] =
          chapname
          .flatMap(cn => Or.from("(\\d+)".r.findFirstIn(cn), One(DecodingFailure(s"$chapname contains no int", Nil))))
          .map(_.toInt)
        val title = chap.get[String]("title").ors
        withGood(Good(volume), num, chapname, title, Good(cid)) {ChapterInfo.apply}
      }.toList.validatedBy(x => x).map(_.sortBy(_.sorting).flatMap {_.contents}).badMap(_.map(JsonDecoding))
    }
  }

  val pageWrapper: Wrapper = {
    JsonWrapper { json =>
      val c = json.hcursor
      val hash = c.get[String]("hash").ors
      val server = c.get[String]("server").ors
      val cid = c.get[Int]("id").ors
      withGood(hash, server, cid) { (hash, server, cid) =>
        c.downField("page_array").values.get.zipWithIndex.map { case (fname, i) =>
          val url = Vurl.fromString(s"$server$hash/${fname.as[String].right.get}")
          ImageRef(url, Vurl.fromString(s"https://mangadex.org/chapter/$cid/${i+1}"))
        }.toList
      }.badMap(_.map(JsonDecoding))
    }
  }


  val extractData = """https://mangadex.(?:org|com)/manga/(\d+)/([^/]+)""".r
  val apiLink = """https://mangadex.(?:org|com)/api/?id=(\d+)&type=manga""".r

  override def toNarrator(nar: MangadexNarrator): NarratorADT = {
    val uri = nar.archiveUri.uriString() match {
      case extractData(num, cid) => apiFromNum(num)
      case apiLink(num) => nar.archiveUri
    }
    Templates.archivePage(nar.id, nar.name, uri, archiveWrapper, pageWrapper)

  }

  val decoder: Decoder[MangadexNarrator] = Decoder.forProduct3("id", "name", "archiveUri")((i, n, a) => MangadexNarrator(i, n, a))
  val encoder: Encoder[MangadexNarrator] = Encoder.forProduct3("id", "name", "archiveUri")(nar => (nar.id, nar.name, nar.archiveUri))

  def apiFromNum(num: String): Vurl = Vurl.fromString(s"https://mangadex.org/api/?id=$num&type=manga")

  override def unapply(url: String): Option[Vurl] =
    url.toString match {
      case extractData(num, cid) => Some(apiFromNum(num))
      case _ => None
    }

  override val wrap: WrapPart[List[MangadexNarrator]] =
    Combination.of(
      Location,
    JsonWrapper { json =>
      extract {
        json.hcursor.downField("manga").downField("title").as[String].right.get
      }
    }
    ){(loc, title) =>
      val id = title.toLowerCase.replaceAll("\\W", "-")
      MangadexNarrator(s"Mangadex_$id", s"[MD] $title", loc) :: Nil
    }

}
