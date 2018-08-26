package viscel.scribe

import viscel.shared.Log

import scala.collection.mutable

object Recheck {
  /** Starts from the entrypoint, traverses the last Link,
    * collect the path, returns the path from the rightmost child to the root. */
  def computeRightmostLinks(book: Book): List[Link] = {

    val seen = mutable.HashSet[Vurl]()

    @scala.annotation.tailrec
    def rightmost(current: PageData, acc: List[Link]): List[Link] = {
      /* Get the last Link for the current PageData  */
      val next = current.contents.reverseIterator.find {
        case Link(loc, _, _) if seen.add(loc) => true
        case _ => false
      } collect { // essentially a typecast …
        case l@Link(_, _, _) => l
      }
      next match {
        case None => acc
        case Some(link) =>
          book.pages.get(link.ref) match {
            case None => link :: acc
            case Some(scribePage) =>
              rightmost(scribePage, link :: acc)
          }
      }
    }

    book.beginning match {
      case None =>
        Log.Scribe.warn(s"Book ${book.id} was emtpy")
        Nil
      case Some(initialPage) =>
        rightmost(initialPage, Nil)
    }

  }
}
