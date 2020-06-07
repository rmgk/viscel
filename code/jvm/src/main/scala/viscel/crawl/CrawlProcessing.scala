package viscel.crawl

import cats.implicits.catsSyntaxOptionId
import viscel.narration.Narrator.Wrapper
import viscel.netzi.{VRequest, VResponse}
import viscel.selektiv.Narration.ContextData
import viscel.selektiv.{Narration, Report}
import viscel.shared.{DataRow, Log, Vurl}
import viscel.store._

import scala.collection.mutable

object CrawlProcessing {

  def init(initialArchive: List[DataRow.Content], book: Book): Option[DataRow] = {
    val entry = book.beginning
    if (entry.isEmpty || entry.get.contents != initialArchive) {
      Some(DataRow(Book.entrypoint, contents = initialArchive))
    } else None
  }


  def processPageResponse(wrapper: Wrapper, request: VRequest, response: VResponse[String]): DataRow = {
    val context = ContextData(request, response)
    val contents =
      try Narration.Interpreter(context)
          .interpret[List[DataRow.Content]](wrapper)
      catch {case r: Report => throw WrappingException(request, response, r)}

    CrawlProcessing.toDataRow(request, response, contents)
  }

  def decider(book: Book): Decider = Decider(recheck = rechecks(book)).addTasks(initialTasks(book))


  def toDataRow(request: VRequest,
                response: VResponse[_],
                contents: List[DataRow.Content]): DataRow = {
    DataRow(request.href,
            response.location.some.filter(_ != request.href),
            response.lastModified,
            response.etag,
            contents)
  }


  def computeTasks(pageData: DataRow, book: Book): List[VRequest] = {
    pageData.contents.collect {
      case l: DataRow.Link if !book.hasPage(l.ref) => VRequest(l.ref, l.data, Some(pageData.ref))
    }
  }

  def initialTasks(book: Book): List[VRequest] =
    book.allLinks.filter(l => !book.hasPage(l.href) || l.context.contains(Decider.Volatile)).toList
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
        case _                                                              => false
      } collect { case l: DataRow.Link => VRequest(l.ref, l.data, Some(current.ref)) }
      next match {
        case None       => acc
        case Some(link) =>
          book.pages.get(link.href) match {
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

