package viscel.crawl

import viscel.narration.Narrator
import viscel.scribe.{Book, Link, Scribe}
import viscel.shared.Log
import viscel.store.BlobStore

import scala.concurrent.{ExecutionContext, Future}


sealed trait CrawlTask
object CrawlTask {
  case class Image(req: VRequest) extends CrawlTask
  case class Page(req: VRequest, from: Link) extends CrawlTask
}


class Crawl(scribe: Scribe,
            blobStore: BlobStore,
            requestUtil: WebRequestInterface)
           (implicit ec: ExecutionContext) {

  def start(narrator: Narrator): Future[Unit] = {
    val book = scribe.findOrCreate(narrator)
    val escritoire = new CrawlProcessing(narrator)

    val pageData = escritoire.init(book)
    val newBook = pageData.fold(book)(scribe.addPageTo(book, _))

    interpret(newBook, escritoire.decider(newBook), escritoire)
  }

  def interpret(book: Book, decider: Decider, escritoire: CrawlProcessing): Future[Unit] = {
    def loop(book: Book, decider: Decider): Future[Unit] = {
      val (decision, nextDecider) = decider.decide()
      decision match {
        case Some(CrawlTask.Image(imageRequest)) =>
          requestUtil.getBytes(imageRequest).flatMap { response =>
            val blobData = escritoire.processImageResponse(response)
            Log.Crawl.trace(s"Processing ${response.location}, storing $blobData")
            blobStore.write(blobData.blob.sha1, response.content)
            val newBook = scribe.addImageTo(book, blobData)
            loop(newBook, nextDecider)
          }
        case Some(CrawlTask.Page(request, from)) =>
          requestUtil.getString(request).flatMap { response =>
            val pageData = escritoire.processPageResponse(book, from, response)
            Log.Crawl.trace(s"Processing ${response.location}, yielding $pageData")
            val newBook = scribe.addPageTo(book, pageData)
            val tasks = escritoire.computeTasks(pageData, newBook)
            loop(newBook, nextDecider.addTasks(tasks))
          }
        case None => Future.successful(())

      }
    }

    loop(book, decider)

  }
}
