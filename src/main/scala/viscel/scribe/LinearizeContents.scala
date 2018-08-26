package viscel.scribe

import viscel.shared.Log

import scala.collection.mutable

object LinearizeContents {

  def size(book: Book): Int = linearizedContents(book).count {
    case Article(_, _) => true
    case _ => false
  }

  def linearizedContents(book: Book): List[ReadableContent] = {

    Log.Scribe.info(s"pages for ${book.id}")

    val seen = mutable.HashSet[Vurl]()

    def unseen(contents: List[WebContent]): List[WebContent] = {
      contents.filter {
        case link@Link(loc, policy, data) => seen.add(loc)
        case _ => true
      }
    }

    @scala.annotation.tailrec
    def flatten(remaining: List[WebContent], acc: List[ReadableContent]): List[ReadableContent] = {
      remaining match {
        case Nil => acc
        case h :: t => h match {
          case Link(loc, policy, data) =>
            book.pages.get(loc) match {
              case None => flatten(t, acc)
              case Some(alp) => flatten(unseen(alp.contents) reverse_::: t, acc)
            }
          case art@ImageRef(ref, origin, data) =>
            val blob = book.blobs.get(ref).map(_.blob)
            flatten(t, Article(art, blob) :: acc)
          case chap@Chapter(_) => flatten(t, chap :: acc)
        }
      }
    }

    book.beginning match {
      case None =>
        Log.Scribe.warn(s"Book ${book.id} was emtpy")
        Nil
      case Some(initialPage) =>
        flatten(unseen(initialPage.contents.reverse), Nil)
    }

  }
}
