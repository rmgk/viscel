package viscel.crawl

import viscel.crawl.CrawlProcessing.{initialTasks, linkTask, rechecks}
import viscel.narration.Narrator
import viscel.narration.interpretation.NarrationInterpretation
import viscel.shared.Log
import viscel.store._
import viscel.store.v4.DataRow
import cats.implicits.catsSyntaxOptionId

import scala.collection.mutable

class CrawlProcessing(narrator: Narrator) {

  def init(book: Book): Option[DataRow] = {
    val entry = book.beginning
    val transformed = narrator.archive.map(RowStoreTransition.transformContent)
    if (entry.isEmpty || entry.get.contents != transformed) {
      Some(DataRow(Vurl.entrypoint, contents = transformed))
    } else None
  }


  def decider(book: Book): Decider = Decider(recheck = rechecks(book)).addTasks(initialTasks(book))

  def processImageResponse(request: VRequest, response: VResponse[Array[Byte]]): DataRow = {
    val contents = List(DataRow.Blob(
      sha1 = BlobStore.sha1hex(response.content),
      mime = response.mime))
    toDataRow(request.href, response, contents)
  }

  def processPageResponse(book: Book, link: Link, response: VResponse[String]): DataRow = {
    val contents = NarrationInterpretation.NI(link, response)
                   .interpret[List[WebContent]](narrator.wrapper)
                   .fold(identity, r => throw WrappingException(link, r))
                   .map(RowStoreTransition.transformContent)

    toDataRow(link.ref, response, contents)
  }
  def toDataRow(request: Vurl,
                        response: VResponse[_],
                        contents: List[DataRow.Content]): DataRow = {
    DataRow(request,
            response.location.some.filter(_ != request),
            response.lastModified,
            response.etag,
            contents)
  }


  def computeTasks(pageData: DataRow, book: Book): List[CrawlTask] = {
    pageData.contents.collect {
      case l: DataRow.Link if !book.hasPage(l.ref) => linkTask(LinkWithReferer(l, pageData.ref))
    }
  }
}

object CrawlProcessing {
  val VolativeStr = Volatile.toString
  def linkTask(lwr: LinkWithReferer): CrawlTask.Page =
    CrawlTask.Page(VRequest(lwr.link.ref, Some(lwr.referer)),
                   Link(lwr.link.ref, lwr.link.data.headOption match {
                     case Some(VolativeStr) => Volatile
                     case otherwise               => Normal
                   },
                        lwr.link.data.filterNot(_ == Volatile.toString)))

  def initialTasks(book: Book): List[CrawlTask] = book.unresolvedLinks.map(linkTask) :::
                                                  book.volatileAndEmptyLinks.map(linkTask)
  def rechecks(book: Book): List[CrawlTask] = computeRightmostLinks(book).map(linkTask)


  /** Starts from the entrypoint, traverses the last Link,
    * collect the path, returns the path from the rightmost child to the root. */
  def computeRightmostLinks(book: Book): List[LinkWithReferer] = {

    val seen = mutable.HashSet[Vurl]()

    @scala.annotation.tailrec
    def rightmost(current: DataRow, acc: List[LinkWithReferer]): List[LinkWithReferer] = {
      /* Get the last Link for the current PageData  */
      val next = current.contents.reverseIterator.find {
        case DataRow.Link(loc, _) if seen.add(loc) => true
        case _                                     => false
      } collect { case l: DataRow.Link => LinkWithReferer(l, current.ref) }
      next match {
        case None       => acc
        case Some(link) =>
          book.pages.get(link.link.ref) match {
            case None          => link :: acc
            case Some(dataRow) =>
              rightmost(dataRow, link :: acc)
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

