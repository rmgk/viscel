package viscel.crawl

import java.time.Instant

import org.jsoup.Jsoup
import viscel.crawl.CrawlProcessing.{imageRefTask, initialTasks, linkTask, rechecks}
import viscel.narration.Narrator
import viscel.narration.interpretation.NarrationInterpretation
import viscel.shared.{Blob, Log}
import viscel.store.{BlobData, BlobStore, Book, ImageRef, Link, PageData, Volatile, Vurl, WebContent}

import scala.collection.mutable

class CrawlProcessing(narrator: Narrator) {

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

    val payload = response.content
    val doc = Jsoup.parse(response.content, response.location.uriString())

    val contents = NarrationInterpretation.NI(link, payload, doc)
                   .interpret[List[WebContent]](narrator.wrapper)
                   .fold(identity, r => throw WrappingException(link, r))


    PageData(response.request.href,
             Vurl.fromString(doc.location()),
             contents = contents,
             date = response.lastModified.getOrElse(Instant.now()))
  }


  def computeTasks(pageData: PageData, book: Book): List[CrawlTask] = {
    pageData.contents.collect {
      case ir@ImageRef(_, _, _) if !book.hasBlob(ir.ref) => imageRefTask(ir)
      case l@Link(_, _, _) if !book.hasPage(l.ref)       => linkTask(l)
    }
  }
}

object CrawlProcessing {
  def imageRefTask(ir: ImageRef): CrawlTask.Image = CrawlTask.Image(VRequest(ir.ref, Some(ir.origin)))
  def linkTask(link: Link): CrawlTask.Page = CrawlTask.Page(VRequest(link.ref, None), link)

  def initialTasks(book: Book): List[CrawlTask] = emptyImageRefs(book).map(imageRefTask) :::
                                                  volatileAndEmptyLinks(book)
                                                  .map(linkTask)
  def rechecks(book: Book): List[CrawlTask] = computeRightmostLinks(book).map(linkTask)


  def pageContents(book: Book): Iterator[WebContent] = {
    book.allPages().flatMap(_.contents)
  }

  def emptyImageRefs(book: Book): List[ImageRef] = pageContents(book).collect {
    case art@ImageRef(ref, _, _) if !book.hasBlob(ref) => art
  }.toList

  def volatileAndEmptyLinks(book: Book): List[Link] = pageContents(book).collect {
    case link@Link(ref, Volatile, _)                => link
    case link@Link(ref, _, _) if !book.hasPage(ref) => link
  }.toList


  /** Starts from the entrypoint, traverses the last Link,
    * collect the path, returns the path from the rightmost child to the root. */
  def computeRightmostLinks(book: Book): List[Link] = {

    val seen = mutable.HashSet[Vurl]()

    @scala.annotation.tailrec
    def rightmost(current: PageData, acc: List[Link]): List[Link] = {
      /* Get the last Link for the current PageData  */
      val next = current.contents.reverseIterator.find {
        case Link(loc, _, _) if seen.add(loc) => true
        case _                                => false
      } collect { // essentially a typecast …
                   case l@Link(_, _, _) => l
                 }
      next match {
        case None       => acc
        case Some(link) =>
          book.pages.get(link.ref) match {
            case None             => link :: acc
            case Some(scribePage) =>
              rightmost(scribePage, link :: acc)
          }
      }
    }

    book.beginning match {
      case None              =>
        Log.Scribe.warn(s"Book ${book.id} was emtpy")
        Nil
      case Some(initialPage) =>
        rightmost(initialPage, Nil)
    }

  }

}

