package viscel.narration.narrators

import io.circe.{Decoder, Encoder}
import org.jsoup.helper.StringUtil
import org.scalactic._
import viscel.narration.Queries._
import viscel.narration.interpretation.NarrationInterpretation.{Append, LinkDataDecision, NarratorADT, Shuffle, WrapPart}
import viscel.narration.{Metarrator, Templates}
import viscel.scribe.{ImageRef, Link, Volatile, Vurl}
import viscel.selection.ReportTools.extract
import viscel.selection.Selection

import scala.util.matching.Regex

case class MangadexNarrator(id: String, name: String, archiveUri: Vurl)

object Mangadex extends Metarrator[MangadexNarrator]("Mangadex") {

  val dataurlR: Regex = """(?s).*var dataurl = '(\w+)';.*""".r
  val pagesR: Regex = """(?s).*var page_array = \[\s+([^\]]+)\];.*""".r
  val pageR: Regex = """'([^']+)'""".r
  val serverR: Regex = """(?s).*var server = '([^']+)';.*""".r

  val chaptersWrapper = Shuffle(queryChapterArchive("#chapters tr:has([title=English]) a[data-chapter-id]"), chapterReverse)
  val otherArchives = Shuffle.reverse(
    Selection
      .all("#chapters .pagination .paging a:not(:has(span))")
      .wrapEach(extractURL(_).map(u => Link(u, Volatile, List("secondary")))))
  val archiveWrapper = LinkDataDecision(_.nonEmpty, chaptersWrapper, Append(otherArchives, chaptersWrapper))
  val pageWrapper = Selection.many("script").wrap { scripts =>
    extract {
      val scriptSource = scripts.last.html()
      val pagesR(pages) = scriptSource
      val dataurlR(dataurl) = scriptSource
      val serverR(server) = scriptSource
      pageR.findAllMatchIn(pages).map { m =>
        val page = m.group(1)
        ImageRef(StringUtil.resolve(scripts.head.ownerDocument().baseUri(), s"$server$dataurl/$page"), scripts.head.ownerDocument().location())
      }.toList
    }
  }


  override def toNarrator(nar: MangadexNarrator): NarratorADT =
    Templates.archivePage(nar.id, nar.name, nar.archiveUri, archiveWrapper, pageWrapper)

  val decoder: Decoder[MangadexNarrator] = Decoder.forProduct3("id", "name", "archiveUri")((i, n, a) => MangadexNarrator(i, n, a))
  val encoder: Encoder[MangadexNarrator] = Encoder.forProduct3("id", "name", "archiveUri")(nar => (nar.id, nar.name, nar.archiveUri))

  override def unapply(vurl: String): Option[Vurl] =
    if (vurl.toString.startsWith("https://mangadex.org/manga/")) Some(Vurl.fromString(vurl)) else None

  val extractID = """https://mangadex.org/manga/\d+/([^/]+)""".r

  override val wrap: WrapPart[List[MangadexNarrator]] =
    Selection.unique("#content > div:first-child h3.panel-title").wrapOne { title =>
      val extractID(id) = title.ownerDocument().baseUri()
      Good(MangadexNarrator(s"Mangadex_$id", s"[MD] ${title.text()}", title.ownerDocument().baseUri()) :: Nil)
    }

}
