package viscel.narration.narrators

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import viscel.narration.Narrator.Wrapper
import viscel.narration.{Metarrator, Narrator, Templates}
import viscel.selektiv.Narration.{ContextW, WrapPart}
import viscel.selektiv.{FixedReport, ReportTools}
import viscel.shared.{DataRow, Vurl}
import viscel.shared.JsoniterCodecs.VurlRw


case class MangadexNarrator(id: String, name: String, archiveUri: Vurl)

object Mangadex extends Metarrator[MangadexNarrator]("Mangadex") {

  case class ChapterInfo(lang_code: String, chapter: String, volume: String, title: String) {
    lazy val chapternumber = {
      "(\\d+)".r.findFirstIn(chapter).map(_.toInt).getOrElse(throw new FixedReport(s"$chapter contains no int"))
    }
    def sorting: (Int, Int, String, String) = (volume.toIntOption.getOrElse(Int.MaxValue), chapternumber, chapter, title)
  }

  case class OverviewInfo(chapter: Map[String, ChapterInfo])

  def contents(id: String, chapter: ChapterInfo): List[DataRow.Content] =
    DataRow.Chapter(s"(${chapter.volume}${if (chapter.volume.isBlank) "" else ": "}${chapter.chapter}) ${chapter.title}") ::
    DataRow.Link(Vurl.fromString(s"https://mangadex.org/api/?id=$id&type=chapter")) ::
    Nil

  val overViewCodec = JsonCodecMaker.make[OverviewInfo]

  val archiveWrapper: Wrapper = {
    ContextW.map { context =>
      val json = ReportTools.extract(readFromString[OverviewInfo](context.response.content)(overViewCodec))
      json.chapter.toList.filter(_._2.lang_code == "gb").sortBy(_._2.sorting).flatMap { case (id, chap) => contents(id, chap) }
    }
  }


  case class Chapter(hash: String, server: String, id: Int, page_array: List[String])

  val chapterCodec = JsonCodecMaker.make[Chapter]


  val pageWrapper: Wrapper = {
    ContextW.map { context =>
      val chapter   = ReportTools.extract(readFromString[Chapter](context.response.content)(chapterCodec))
      val server    = chapter.server
      val absServer = if (server.startsWith("/")) s"https://mangadex.org$server" else server
      chapter.page_array.zipWithIndex.map { case (fname, i) =>
        DataRow.Link(Vurl.fromString(s"$absServer${chapter.hash}/${fname}"))
      }
    }
  }


  val extractData = """https://mangadex.(?:org|com)/(?:manga|title)/(\d+)/([^/]+)""".r
  val apiLink     = """https://mangadex.(?:org|com)/api/\?id=(\d+)&type=manga""".r

  override def toNarrator(nar: MangadexNarrator): Narrator = {
    val uri = nar.archiveUri.uriString() match {
      case extractData(num, cid) => apiFromNum(num)
      case apiLink(num)          => nar.archiveUri
    }
    Templates.archivePage(nar.id, nar.name, uri, archiveWrapper, pageWrapper)
  }


  override val codec: JsonValueCodec[MangadexNarrator] = JsonCodecMaker.make

  def apiFromNum(num: String): Vurl = Vurl.fromString(s"https://mangadex.org/api/?id=$num&type=manga")

  override def unapply(url: String): Option[Vurl] =
    url.toString match {
      case extractData(num, cid) => Some(apiFromNum(num))
      case _                     => None
    }

  case class Manga(title: String)
  case class MangaInfo(manga: Manga)
  val mangaCodec = JsonCodecMaker.make[MangaInfo]

  override val wrap: WrapPart[List[MangadexNarrator]] =
    ContextW.map { context =>
      val loc  = context.location
      val json = readFromString[MangaInfo](context.response.content)(mangaCodec)


      val id = json.manga.title.toLowerCase.replaceAll("\\W", "-")
      MangadexNarrator(s"Mangadex_$id", s"[MD] ${json.manga.title}", loc) :: Nil
    }

}
