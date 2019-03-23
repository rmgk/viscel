package viscel.server

import viscel.shared.{Blob, ChapterPos, Contents, Description, Gallery, Log, SharedImage, Vid}
import viscel.store._
import viscel.store.v4.DataRow

import cats.implicits.catsSyntaxOptionId

import scala.collection.mutable

class ContentLoader(narratorCache: NarratorCache,
                    rowStore: RowStore,
                    descriptionCache: DescriptionCache) {


  def contents(id: Vid): Option[Contents] = {
    // load the book in an order suitable for viewing
    val pages = ContentLoader.linearizedPages(rowStore.loadBook(id))
    if (pages.isEmpty) None
    else Some(ContentLoader.pagesToContents(pages))
  }

  def descriptions(): Set[Description] = {
    val books = rowStore.allVids().map {description}
    var known = books.map(d => d.id -> d).toMap
    val nars = narratorCache.all.map { n =>
      known.get(n.id) match {
        case None       => Description(n.id, n.name, 0, unknownNarrator = false)
        case Some(desc) =>
          known = known - n.id
          desc.copy(unknownNarrator = false)
      }
    }
    nars ++ known.values
  }

  private def description(id: Vid): Description = descriptionCache.getOrElse(id) {
    val book = rowStore.loadBook(id)
    Description(id, book.name, ContentLoader.size(book), unknownNarrator = true)
  }


}

object ContentLoader {

  def size(book: Book): Int = ContentLoader.linearizedPages(book).count {_.isInstanceOf[Article]}

  case class OriginData(link: DataRow.Link) {
    def dataMap: Map[String, String] = link.data.sliding(2, 2).filter(_.size == 2).map {
      case List(a, b) => a -> b
    }.toMap
  }

  def linearizedPages(book: Book): List[ReadableContent] = {

    //Log.Scribe.info(s"pages for ${book.id}")

    val seenOrigins = mutable.HashMap[Vurl, Vurl]()


    def unseen(origin: Vurl, contents: List[DataRow.Content]): List[DataRow.Content] = {
      contents.filter {
        case link @ DataRow.Link(loc, data) =>
          if (seenOrigins.contains(loc)) false
          else {
            seenOrigins += (loc -> origin)
            true
          }
        case _                              => true
      }
    }

    @scala.annotation.tailrec
    def flatten(lastLink: Option[OriginData],
                remaining: List[DataRow.Content],
                acc: List[ReadableContent]): List[ReadableContent] = {
      remaining match {
        case Nil    => acc
        case h :: t => h match {
          case l @ DataRow.Link(loc, _) =>
            book.pages.get(loc) match {
              case None      => flatten(lastLink, t, acc)
              case Some(alp) => flatten(OriginData(l).some,
                                        unseen(alp.ref, alp.contents) reverse_::: t,
                                        acc)
            }
          case DataRow.Blob(sha1, mime) =>
            val ll = lastLink.get
            flatten(lastLink, t,
                    Article(
                      ImageRef(ll.link.ref,
                               seenOrigins(ll.link.ref),
                               ll.dataMap
                      ), Some(Blob(sha1, mime))) :: acc)
          case DataRow.Chapter(str)     => flatten(lastLink, t, Chapter(str) :: acc)
        }
      }
    }

    book.beginning match {
      case None              =>
        Log.Scribe.warn(s"Book ${book.id} was empty")
        Nil
      case Some(initialPage) =>
        flatten(None, unseen(initialPage.ref, initialPage.contents.reverse), Nil)
    }

  }

  def pagesToContents(pages: List[ReadableContent]): Contents = {
    @scala.annotation.tailrec
    def recurse(content: List[ReadableContent],
                art: List[SharedImage],
                chap: List[ChapterPos],
                c: Int)
    : (List[SharedImage], List[ChapterPos]) = {
      content match {
        case Nil    => (art, chap)
        case h :: t =>
          h match {
            case Article(ImageRef(_, origin, data), blob) =>
              val article = SharedImage(origin = origin.uriString, blob, data)
              recurse(t, article :: art, if (chap.isEmpty) List(ChapterPos("", 0)) else chap, c + 1)
            case Chapter(name)                            => recurse(t,
                                                                     art,
                                                                     ChapterPos(name,
                                                                                c) :: chap,
                                                                     c)
          }
      }
    }

    val (articles, chapters) = recurse(pages, Nil, Nil, 0)
    Contents(Gallery.fromSeq(articles.reverse), chapters)
  }

}
