package viscel.narration.narrators

import io.circe.{Decoder, Encoder}
import viscel.narration.interpretation.NarrationInterpretation.{Combination, JsonWrapper, Location, NarratorADT, WrapPart, Wrapper}
import viscel.narration.{Metarrator, Templates}
import viscel.selection.ReportTools.extract
import viscel.store.{Chapter, ImageRef, Link, Vurl}

case class MangadexNarrator(id: String, name: String, archiveUri: Vurl)

object Mangadex extends Metarrator[MangadexNarrator]("Mangadex") {

  val archiveWrapper: Wrapper = {
    JsonWrapper { json =>
      extract {
        val chaptersMap = json.hcursor.downField("chapter")
        chaptersMap.keys.get.collect(Function.unlift { cid =>
          val chap = chaptersMap.downField(cid)
          val lang = chap.get[String]("lang_code").right.get
          val vol = chap.get[String]("volume").right.get.toInt
          val chapname = chap.get[String]("chapter").right.get
          val firstnum = "(\\d+)".r.findFirstIn(chapname).get.toInt
          if (lang != "gb") None
          else Some((vol, firstnum, chapname, cid))
        }).toList.sorted.flatMap { chap =>
          Chapter(s"${chap._1}: ${chap._3}") ::
          Link(Vurl.fromString(s"https://mangadex.org/api/?id=${chap._4}&type=chapter")) ::
          Nil
        }

      }
    }
  }

  val pageWrapper: Wrapper = {
    Combination.of(Location,
                   JsonWrapper { json =>
                     val c = json.hcursor
                     extract {
                       val hash = c.get[String]("hash").right.get
                       val server = c.get[String]("server").right.get
                       c.downField("page_array").values.get.map { fname =>
                         Vurl.fromString(s"$server$hash/${fname.as[String].right.get}")
                       }.toList
                     }
                   }) { (loc, vurls) => vurls.map(ImageRef(_, loc)) }
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
