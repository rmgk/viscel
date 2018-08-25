package viscel.scribe

import java.time.Instant

import org.jsoup.Jsoup
import viscel.crawl.{CrawlTask, VRequest, VResponse, WrappingException}
import viscel.narration.Narrator
import viscel.narration.interpretation.NarrationInterpretation
import viscel.shared.{Blob, Log}
import viscel.store.BlobStore

class ScribeNarratorAdapter(scribe: Scribe, narrator: Narrator, blobStore: BlobStore) {
  @volatile private var book: Book = scribe.findOrCreate(narrator)

  private def imageRefTask(ir: ImageRef): CrawlTask.Image = CrawlTask.Image(VRequest(ir.ref, Some(ir.origin)))
  private def linkTask(link: Link): CrawlTask.Page = CrawlTask.Page(VRequest(link.ref, None), link)

  def initialTasks(): List[CrawlTask] = book.emptyImageRefs().map(imageRefTask) ::: book.volatileAndEmptyLinks().map(linkTask)
  def rechecks(): List[CrawlTask] = Recheck.computeRightmostLinks(book).map(linkTask)

  def init(): Unit = {
    val entry = book.beginning
    if (entry.isEmpty || entry.get.contents != narrator.archive) {
      book = scribe.addPageTo(book,
                      PageData(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
    }
  }

  def storeImage(response: VResponse[Array[Byte]]): List[VRequest] = {

    val sha1 = blobStore.write(response.content)
    val blob = BlobData(response.request.href, response.location,
                        blob = Blob(
                          sha1 = sha1,
                          mime = response.mime),
                        date = response.lastModified.getOrElse(Instant.now()))

    book = scribe.addImageTo(book, blob)
    Nil
  }


  def storePage(link: Link)(response: VResponse[String]): List[CrawlTask] = {

    val doc = Jsoup.parse(response.content, response.location.uriString())

    val contents = NarrationInterpretation
                   .interpret[List[WebContent]](narrator.wrapper, doc, link)
                   .fold(identity, r => throw WrappingException(link, r))


    Log.Clockwork.trace(s"request page ${response.location}, yielding $contents")
    val page = PageData(response.request.href,
                        Vurl.fromString(doc.location()),
                        contents = contents,
                        date = response.lastModified.getOrElse(Instant.now()))

    book = scribe.addPageTo(book, page)
    contents.collect {
      case ir@ImageRef(_, _, _) if !book.hasBlob(ir.ref) => imageRefTask(ir)
      case l@Link(_, _, _) if !book.hasPage(l.ref) => linkTask(l)
    }
  }


}
