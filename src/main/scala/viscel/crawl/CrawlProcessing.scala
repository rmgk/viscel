package viscel.crawl

import cats.implicits.catsSyntaxOptionId
import viscel.crawl.CrawlProcessing.{initialTasks, rechecks}
import viscel.narration.Narrator
import viscel.netzi.{NarrationInterpretation, VRequest, VResponse, Vurl}
import viscel.shared.Log
import viscel.store._
import viscel.store.v3.Volatile
import viscel.store.v4.DataRow

import scala.collection.mutable

class CrawlProcessing(narrator: Narrator) {

  def init(book: Book): Option[DataRow] = {
    val entry = book.beginning
    val transformed = narrator.archive
    if (entry.isEmpty || entry.get.contents != transformed) {
      Some(DataRow(Vurl.entrypoint, contents = transformed))
    } else None
  }


  def decider(book: Book): Decider = Decider(recheck = rechecks(book)).addTasks(initialTasks(book))

  def processPageResponse(book: Book, link: DataRow.Link, response: VResponse[String]): DataRow = {
    val contents = NarrationInterpretation.NI(link, response)
                   .interpret[List[DataRow.Content]](narrator.wrapper)
                   .fold(identity, r => throw WrappingException(link, r))

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


  def computeTasks(pageData: DataRow, book: Book): List[VRequest] = {
    pageData.contents.collect {
      case l: DataRow.Link if !book.hasPage(l.ref) => VRequest(l, Some(pageData.ref))
    }
  }
}

object CrawlProcessing {
  val VolatileStr = Volatile.toString

  def initialTasks(book: Book): List[VRequest] =
    book.allLinks.filter(l => !book.hasPage(l.link.ref) || l.link.data.contains(VolatileStr)).toList
  def rechecks(book: Book): List[VRequest] = computeRightmostLinks(book)


  /** Starts from the entrypoint, traverses the last Link,
    * collect the path, returns the path from the rightmost child to the root. */
  def computeRightmostLinks(book: Book): List[VRequest] = {

    val seen = mutable.HashSet[Vurl]()

    @scala.annotation.tailrec
    def rightmost(current: DataRow, acc: List[VRequest]): List[VRequest] = {
      /* Get the last Link for the current PageData  */
      val next = current.contents.reverseIterator.find {
        case DataRow.Link(loc, _) if seen.add(loc) && book.notJustBlob(loc) => true
        case _                                     => false
      } collect { case l: DataRow.Link => VRequest(l, Some(current.ref)) }
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
        Log.Scribe.warn(s"Book ${book.id} was empty")
        Nil
      case Some(initialPage) =>
        rightmost(initialPage, Nil)
    }

  }

}

