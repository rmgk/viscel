package viscel.crawl

import viscel.narration.Narrator
import viscel.netzi.{VRequest, VResponse, WebRequestInterface}
import viscel.shared.Log
import viscel.store.v4.{DataRow, RowAppender, RowStoreV4}
import viscel.store.{BlobStore, Book, DescriptionCache}

import scala.concurrent.{ExecutionContext, Future}


class CrawlServices(blobStore: BlobStore,
                    requestUtil: WebRequestInterface,
                    rowStore: RowStoreV4,
                    descriptionCache: DescriptionCache,
                    executionContext: ExecutionContext) {

  def startCrawling(narrator: Narrator): Future[Unit] = {
    val appender = rowStore.open(narrator)
    val book = rowStore.loadBook(narrator.id)

    val pageData = CrawlProcessing.init(narrator.archive, book)
    val newBook = pageData.fold(book)(addPageTo(book, appender, _))

    new Crawling(narrator, appender).interpret(newBook, CrawlProcessing.decider(newBook))
  }

  def addPageTo(book: Book,
                rowAppender: RowAppender,
                pageData: DataRow): Book = {
    val (newBook, written) = book.addPage(pageData)
    written.foreach { i =>
      rowAppender.append(pageData)
      descriptionCache.updateSize(newBook.id, i)
    }
    newBook
  }


  class Crawling(narrator: Narrator, rowAppender: RowAppender) {

    def interpret(book: Book, decider: Decider): Future[Unit] = {
      decider.decide() match {
        case Some((request, nextDecider)) => handlePageResponse(book, request, nextDecider)
        case None                         => Future.successful(())
      }
    }

    private def handlePageResponse(book: Book,
                                   request: VRequest,
                                   decider: Decider): Future[Unit] = {
      Log.Crawl.trace(s"Handling page response for $request")
      requestUtil.get(request).flatMap { response: VResponse[Either[Array[Byte], String]] =>
        response.content match {
          case Left(array) =>
            val sha1 = BlobStore.sha1hex(array)
            val contents = List(DataRow.Blob(sha1 = sha1, mime = response.mime))
            val datarow = CrawlProcessing.toDataRow(request, response, contents)
            Log.Crawl.trace(s"Processing ${response.location}, storing $sha1")
            blobStore.write(sha1, array)
            interpret(addPageTo(book, rowAppender, datarow), decider)
          case Right(str)  =>
            val pageData = CrawlProcessing.processPageResponse(narrator.wrapper,
                                                               request,
                                                               response.copy(content = str))
            Log.Crawl.trace(s"Processing ${response.location}, yielding $pageData")
            val newBook: Book = addPageTo(book, rowAppender, pageData)
            val tasks = CrawlProcessing.computeTasks(pageData, newBook)
            Log.Crawl.trace(s"Add tasks: $tasks")
            interpret(newBook, decider.addTasks(tasks))
        }

      }(executionContext)
    }
  }


}



