package viscel.crawl

import viscel.narration.Narrator
import viscel.scribe.{Book, Link, PageData, RowAppender, RowStore, Scribe}
import viscel.shared.Log
import viscel.store.{BlobStore, DescriptionCache}

import scala.concurrent.{ExecutionContext, Future}


sealed trait CrawlTask
object CrawlTask {
  case class Image(req: VRequest) extends CrawlTask
  case class Page(req: VRequest, from: Link) extends CrawlTask
}


class Crawl(scribe: Scribe,
            blobStore: BlobStore,
            requestUtil: WebRequestInterface,
            rowStore: RowStore,
            descriptionCache: DescriptionCache)
           (implicit ec: ExecutionContext) {

  def start(narrator: Narrator): Future[Unit] = {
    val appender = rowStore.open(narrator)
    val book = Book.load(narrator.id, rowStore).get
    val escritoire = new CrawlProcessing(narrator)

    val pageData = escritoire.init(book)
    val newBook = pageData.fold(book)(addPageTo(book, appender, _))

    interpret(newBook, escritoire.decider(newBook), escritoire, appender)
  }

  def interpret(book: Book, decider: Decider, escritoire: CrawlProcessing, rowAppender: RowAppender): Future[Unit] = {
    val (decision, nextDecider) = decider.decide()
    decision match {
      case Some(CrawlTask.Image(imageRequest)) =>
        requestUtil.getBytes(imageRequest).flatMap { response =>
          val blobData = escritoire.processImageResponse(response)
          Log.Crawl.trace(s"Processing ${response.location}, storing $blobData")
          blobStore.write(blobData.blob.sha1, response.content)
          rowAppender.append(blobData)
          val newBook = book.addBlob(blobData)
          interpret(newBook, nextDecider, escritoire, rowAppender)
        }
      case Some(CrawlTask.Page(request, from)) =>
        requestUtil.getString(request).flatMap { response =>
          val pageData = escritoire.processPageResponse(book, from, response)
          Log.Crawl.trace(s"Processing ${response.location}, yielding $pageData")
          val newBook: Book = addPageTo(book, rowAppender, pageData)
          val tasks = escritoire.computeTasks(pageData, newBook)
          interpret(newBook, nextDecider.addTasks(tasks), escritoire, rowAppender)
        }
      case None                                => Future.successful(())

    }
  }

  private def addPageTo(book: Book,
                        rowAppender: RowAppender,
                        pageData: PageData) = {
    val (newBook, written) = book.addPage(pageData)
    written.foreach { i =>
      rowAppender.append(pageData)
      descriptionCache.updateSize(newBook.id, i)
    }
    newBook
  }
}
