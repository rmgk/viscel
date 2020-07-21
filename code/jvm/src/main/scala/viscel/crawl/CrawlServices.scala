package viscel.crawl

import java.util.concurrent.CancellationException

import viscel.narration.Narrator
import viscel.netzi.{VRequest, VResponse, WebRequestInterface}
import viscel.shared.{DataRow, Log}
import viscel.store.{BlobStore, Book, DescriptionCache, RowAppender, RowStoreV4}

import scala.concurrent.{ExecutionContext, Future}

class CrawlServices(
    blobStore: BlobStore,
    requestUtil: WebRequestInterface,
    rowStore: RowStoreV4,
    descriptionCache: DescriptionCache,
    executionContext: ExecutionContext
) {

  @volatile private var cancel: Boolean = false

  def shutdown() = cancel = true

  def startCrawling(narrator: Narrator): Future[Unit] = {
    val appender = rowStore.open(narrator)
    val book     = rowStore.loadBook(narrator.id)

    val pageData = CrawlProcessing.init(narrator.archive, book)
    val newBook  = pageData.fold(book)(addPageTo(book, appender, _))

    new Crawling(narrator, appender).crawlLoop(CrawlState(newBook, CrawlProcessing.decider(newBook)))
  }

  def addPageTo(book: Book, rowAppender: RowAppender, pageData: DataRow): Book = {
    book.addPage(pageData) match {
      case None => book
      case Some(newBook) =>
        rowAppender.append(pageData)
        descriptionCache.invalidate(newBook.id)
        newBook
    }
  }

  case class CrawlState(book: Book, decider: Decider)

  class Crawling(narrator: Narrator, rowAppender: RowAppender) {

    def crawlLoop(cs: CrawlState): Future[Unit] = {
      if (cancel) return Future.failed(new CancellationException("orderly shutdown"))
      cs.decider.decide() match {
        case Some((request, nextDecider)) =>
          requestUtil.get(request).flatMap { response =>
            val nextState: CrawlState = handleResponse(cs.book, response, request, nextDecider)
            crawlLoop(nextState)
          }(executionContext)
        case None => Future.successful(())
      }
    }

    def handleResponse(
        book: Book,
        response: VResponse[Either[Array[Byte], String]],
        request: VRequest,
        decider: Decider
    ): CrawlState = {

      def handleBlob(array: Array[Byte]): CrawlState = {
        val sha1     = BlobStore.sha1hex(array)
        val contents = List(DataRow.Blob(sha1 = sha1, mime = response.mime))
        val datarow  = CrawlProcessing.toDataRow(request, response, contents)
        Log.Crawl.trace(s"Processing ${response.location}, storing $sha1")
        blobStore.write(sha1, array)
        CrawlState(addPageTo(book, rowAppender, datarow), decider)
      }

      def handlePage(str: String): CrawlState = {
        val pageData = CrawlProcessing.processPageResponse(narrator.wrapper, request, response.copy(content = str))
        Log.Crawl.trace(s"Processing ${response.location}, yielding $pageData")
        val newBook: Book = addPageTo(book, rowAppender, pageData)
        val tasks         = CrawlProcessing.computeTasks(pageData, newBook)
        Log.Crawl.trace(s"Add tasks: $tasks")
        CrawlState(newBook, decider.addTasks(tasks))
      }

      Log.Crawl.trace(s"Handling page response for $request")
      response.content match {
        case Left(array) => handleBlob(array)
        case Right(str)  => handlePage(str)
      }
    }

  }
}
