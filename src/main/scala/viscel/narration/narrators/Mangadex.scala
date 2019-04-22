package viscel.narration.narrators

import cats.implicits._
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder}
import viscel.narration.Narrator.Wrapper
import viscel.narration.{Metarrator, NarratorADT, Templates}
import viscel.selektiv.Narration.{Combination, ContextW, WrapPart}
import viscel.selektiv.Report
import viscel.store.v4.{DataRow, Vurl}

import scala.util.Try

case class MangadexNarrator(id: String, name: String, archiveUri: Vurl)

object Mangadex extends Metarrator[MangadexNarrator]("Mangadex") {

  case class ChapterInfo(volume: Option[Int], num: Int, numStr: String, title: String, id: String) {
    def sorting: (Int, Int, String, String, String) = (volume.getOrElse(Int.MaxValue), num, numStr, title, id)
    def contents: List[DataRow.Content] =
      DataRow.Chapter(s"(${volume.fold("")(i => s"$i: ")}$num) $title") ::
      DataRow.Link(Vurl.fromString(s"https://mangadex.org/api/?id=$id&type=chapter")) ::
      Nil
  }


  case class JsonDecoding(decodingFailure: DecodingFailure) extends Report {
    override def describe: String = decodingFailure.getMessage()
  }


  val archiveWrapper: Wrapper = {
    ContextW.map { context =>
      val json = io.circe.parser.parse(context.content).right.get
      val chaptersMap = json.hcursor.downField("chapter")
      chaptersMap.keys.get
      .filter(cid => chaptersMap.downField(cid).get[String]("lang_code").getOrElse("") == "gb")
      .map { cid =>
        val chap = chaptersMap.downField(cid)
        val chapname: Result[String] = chap.get[String]("chapter")
        val volume = chap.get[String]("volume").toOption.flatMap(v => Try(v.toInt).toOption)
        val num =
          chapname
          .flatMap(cn => "(\\d+)".r.findFirstIn(cn)
                                 .toRight(DecodingFailure(s"$chapname contains no int", Nil)))
          .map(_.toInt)
        val title = chap.get[String]("title")
        val tuple: (Result[Option[Int]], Result[Int], Result[String], Result[String], Result[String]) =
          (volume.asRight, num, chapname, title, cid.asRight)
        tuple.mapN {ChapterInfo.apply}
      }.toList.sequence
        .map(_.sortBy(_.sorting).flatMap {_.contents})
        .leftMap(JsonDecoding)
        .right.get
    }
  }

  val pageWrapper: Wrapper = {
    ContextW.map { context =>
      val json = io.circe.parser.parse(context.content).right.get
      val c = json.hcursor
      val hash = c.get[String]("hash")
      val server = c.get[String]("server")
      val cid = c.get[Int]("id")
      (hash, server, cid).mapN { (hash, server, cid) =>
        val absServer = if (server.startsWith("/")) s"https://mangadex.org$server" else server
        c.downField("page_array").values.get.zipWithIndex.map { case (fname, i) =>
          val url = Vurl.fromString(s"$absServer$hash/${fname.as[String].right.get}")
          DataRow.Link(url)
        }.toList
      }.leftMap(JsonDecoding).right.get
    }
  }


  val extractData = """https://mangadex.(?:org|com)/(?:manga|title)/(\d+)/([^/]+)""".r
  val apiLink = """https://mangadex.(?:org|com)/api/\?id=(\d+)&type=manga""".r

  override def toNarrator(nar: MangadexNarrator): NarratorADT = {
    val uri = nar.archiveUri.uriString() match {
      case extractData(num, cid) => apiFromNum(num)
      case apiLink(num) => nar.archiveUri
    }
    Templates.archivePage(nar.id, nar.name, uri, archiveWrapper, pageWrapper)

  }

  val decoder: Decoder[MangadexNarrator] = Decoder.forProduct3("id", "name", "archiveUri")(
    (i, n, a) => MangadexNarrator(i, n, a))
  val encoder: Encoder[MangadexNarrator] = Encoder.forProduct3("id", "name", "archiveUri")(
    nar => (nar.id, nar.name, nar.archiveUri))

  def apiFromNum(num: String): Vurl = Vurl.fromString(s"https://mangadex.org/api/?id=$num&type=manga")

  override def unapply(url: String): Option[Vurl] =
    url.toString match {
      case extractData(num, cid) => Some(apiFromNum(num))
      case _ => None
    }

  override val wrap: WrapPart[List[MangadexNarrator]] =
    Combination.of(
      ContextW.map(_.location),
      ContextW.map { context =>
        val json = io.circe.parser.parse(context.content).right.get
        json.hcursor.downField("manga").downField("title").as[String].right.get
    }
      ){(loc, title) =>
      val id = title.toLowerCase.replaceAll("\\W", "-")
      MangadexNarrator(s"Mangadex_$id", s"[MD] $title", loc) :: Nil
    }

}
