package viscel.crawl

import java.time.Instant

import org.jsoup.Jsoup
import viscel.narration.Narrator
import viscel.narration.interpretation.NarrationInterpretation
import viscel.scribe.{BlobData, Book, ImageRef, Link, PageData, Recheck, Volatile, Vurl, WebContent}
import viscel.shared.Blob
import viscel.store.BlobStore
import viscel.crawl.Escritoire.{initialTasks, rechecks, imageRefTask, linkTask}

class Escritoire(narrator: Narrator) {

  def init(book: Book): Option[PageData] = {
    val entry = book.beginning
    if (entry.isEmpty || entry.get.contents != narrator.archive) {
      Some(PageData(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
    } else None
  }


  def decider(book: Book): Decider = Decider(recheck = rechecks(book)).addTasks(initialTasks(book))

  def processImageResponse(response: VResponse[Array[Byte]]): BlobData = {
    BlobData(response.request.href, response.location,
             blob = Blob(
               sha1 = BlobStore.sha1hex(response.content),
               mime = response.mime),
             date = response.lastModified.getOrElse(Instant.now()))
  }


  def processPageResponse(book: Book, link: Link, response: VResponse[String]): PageData = {

    val doc = Jsoup.parse(response.content, response.location.uriString())

    val contents = NarrationInterpretation
                   .interpret[List[WebContent]](narrator.wrapper, doc, link)
                   .fold(identity, r => throw WrappingException(link, r))


    PageData(response.request.href,
                        Vurl.fromString(doc.location()),
                        contents = contents,
                        date = response.lastModified.getOrElse(Instant.now()))
  }


  def computeTasks(pageData: PageData, book: Book): List[CrawlTask] = {
    pageData.contents.collect {
      case ir@ImageRef(_, _, _) if !book.hasBlob(ir.ref) => imageRefTask(ir)
      case l@Link(_, _, _) if !book.hasPage(l.ref) => linkTask(l)
    }
  }
}

object Escritoire {
  def imageRefTask(ir: ImageRef): CrawlTask.Image = CrawlTask.Image(VRequest(ir.ref, Some(ir.origin)))
  def linkTask(link: Link): CrawlTask.Page = CrawlTask.Page(VRequest(link.ref, None), link)

  def initialTasks(book: Book): List[CrawlTask] = emptyImageRefs(book).map(imageRefTask) ::: volatileAndEmptyLinks(book).map(linkTask)
  def rechecks(book: Book): List[CrawlTask] = Recheck.computeRightmostLinks(book).map(linkTask)


  def pageContents(book: Book): Iterator[WebContent] = {
    book.allPages().flatMap(_.contents)
  }

  def emptyImageRefs(book: Book): List[ImageRef] = pageContents(book).collect {
    case art@ImageRef(ref, _, _) if !book.hasBlob(ref) => art
  }.toList

  def volatileAndEmptyLinks(book: Book): List[Link] = pageContents(book).collect {
    case link@Link(ref, Volatile, _) => link
    case link@Link(ref, _, _) if !book.hasPage(ref) => link
  }.toList

}

