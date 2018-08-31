package viscel.crawl

import viscel.narration.Narrator
import viscel.shared.Log
import viscel.store.{BlobStore, Book, DescriptionCache, Link, PageData, RowAppender, RowStore}

import scala.concurrent.{ExecutionContext, Future}


sealed trait CrawlTask
object CrawlTask {
  case class Image(req: VRequest) extends CrawlTask
  case class Page(req: VRequest, from: Link) extends CrawlTask
}


class Crawl(blobStore: BlobStore,
            requestUtil: WebRequestInterface,
            rowStore: RowStore,
            descriptionCache: DescriptionCache)
           (implicit ec: ExecutionContext) {

  def start(narrator: Narrator): Future[Unit] = {
    val appender = rowStore.open(narrator)
    val book = rowStore.load(narrator.id)
    val processing = new CrawlProcessing(narrator)

    val pageData = processing.init(book)
    val newBook = pageData.fold(book)(addPageTo(book, appender, _))

    new Crawling(processing, appender).interpret(newBook, processing.decider(newBook))
  }

  class Crawling(processing: CrawlProcessing, rowAppender: RowAppender) {

    def interpret(book: Book, decider: Decider): Future[Unit] = {
      val (decision, nextDecider) = decider.decide()
      decision match {
        case Some(CrawlTask.Image(imageRequest)) =>
          handleImageResponse(nextDecider, imageRequest, book)
        case Some(CrawlTask.Page(request, from)) =>
          handlePageResponse(book, request, from, nextDecider)
        case None                                => Future.successful(())

      }
    }

    private def handlePageResponse(book: Book,
                                   request: VRequest,
                                   from: Link,
                                   decider: Decider): Future[Unit] = {
      Log.Crawl.trace(s"Handling page response for $from, $request")
      requestUtil.getString(request).flatMap { response =>
        val pageData = processing.processPageResponse(book, from, response)
        Log.Crawl.trace(s"Processing ${response.location}, yielding $pageData")
        val newBook: Book = addPageTo(book, rowAppender, pageData)
        val tasks = processing.computeTasks(pageData, newBook)
        Log.Crawl.trace(s"Add tasks: $tasks")
        interpret(newBook, decider.addTasks(tasks))
      }
    }
    private def handleImageResponse(decider: Decider,
                                    imageRequest: VRequest,
                                    book: Book): Future[Unit] = {
      requestUtil.getBytes(imageRequest).flatMap { response =>
        val blobData = processing.processImageResponse(response)
        Log.Crawl.trace(s"Processing ${response.location}, storing $blobData")
        blobStore.write(blobData.blob.sha1, response.content)
        rowAppender.append(blobData)
        interpret(book.addBlob(blobData), decider)
      }
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
