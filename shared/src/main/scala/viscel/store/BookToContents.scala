package viscel.store

import viscel.shared.{Blob, ChapterPos, Contents, DataRow, Log, SharedImage, Vurl}

import scala.collection.mutable

object BookToContents {

  def size(book: Book): Int = BookToContents.linearizedPages(book).count(_.isLeft)

  def contents(book: Book): Option[Contents] = {
    val pages = BookToContents.linearizedPages(book)
    if (pages.isEmpty) None
    else Some(BookToContents.pagesToContents(pages))
  }

  type LinearResult = List[Either[SharedImage, DataRow.Chapter]]
  def linearizedPages(book: Book): LinearResult = {
    val start = System.currentTimeMillis()

    val seenOrigins = mutable.HashMap[Vurl, Vurl]()

    def unseen(origin: Vurl, contents: List[DataRow.Content]): List[DataRow.Content] = {
      contents.filter {
        case link @ DataRow.Link(loc, data) =>
          if (seenOrigins.contains(loc)) false
          else {
            seenOrigins += (loc -> origin)
            true
          }
        case _ => true
      }
    }

    def toSharedImage(lastLink: Option[DataRow.Link], blob: DataRow.Blob) = {
      val DataRow.Blob(sha1: String, mime: String) = blob
      val (dataMap, origin) = lastLink.map { ll =>
        ll.data.sliding(2, 2).filter(_.size == 2).collect {
          case List(a, b) => a -> b
        }.toMap -> seenOrigins(ll.ref).uriString()
      }.getOrElse(Map.empty -> "")
      SharedImage(origin, Blob(sha1, mime), dataMap)
    }

    @scala.annotation.tailrec
    def flatten(lastLink: Option[DataRow.Link], remaining: List[DataRow.Content], acc: LinearResult): LinearResult = {
      remaining match {
        case Nil => acc
        case h :: t => h match {
            case l @ DataRow.Link(loc, _) =>
              book.pages.get(loc) match {
                case None => flatten(lastLink, t, acc)
                case Some(alp) =>
                  val unsennContents = unseen(alp.ref, alp.contents)
                  flatten(Some(l), unsennContents reverse_::: t, acc)
              }

            case blob: DataRow.Blob =>
              flatten(lastLink, t, Left(toSharedImage(lastLink, blob)) :: acc)
            case ch: DataRow.Chapter => flatten(lastLink, t, Right(ch) :: acc)
          }
      }
    }

    val res = book.beginning match {
      case None =>
        Log.Scribe.warn(s"Book ${book.id} was empty")
        Nil
      case Some(initialPage) =>
        flatten(None, unseen(initialPage.ref, initialPage.contents.reverse), Nil)
    }
    Log.Store.trace(s"linearized ${book.id} (${System.currentTimeMillis() - start}ms)")
    res
  }

  def pagesToContents(pages: LinearResult): Contents = {
    @scala.annotation.tailrec
    def recurse(
        content: LinearResult,
        images: List[SharedImage],
        chapters: List[ChapterPos],
        counter: Int
    ): (List[SharedImage], List[ChapterPos]) = {
      content match {
        case Nil => (images, chapters)
        case h :: t =>
          h match {
            case Left(article) =>
              recurse(t, article :: images, if (chapters.isEmpty) List(ChapterPos("", 0)) else chapters, counter + 1)
            case Right(chap) =>
              recurse(t, images, ChapterPos(chap.name, counter) :: chapters, counter)
          }
      }
    }

    val (articles, chapters) = recurse(pages, Nil, Nil, 0)
    Contents(articles.reverse, chapters)
  }

}
