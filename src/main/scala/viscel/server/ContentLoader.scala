package viscel.server

import viscel.shared.{ChapterPos, Contents, Description, Gallery, Log, SharedImage, Vid}
import viscel.store.{Article, Book, Chapter, DescriptionCache, ImageRef, Link, NarratorCache, ReadableContent, RowStore, Vurl, WebContent}

import scala.collection.mutable

class ContentLoader(narratorCache: NarratorCache, rowStore: RowStore, descriptionCache: DescriptionCache) {


  private def description(id: Vid): Description = descriptionCache.getOrElse(id) {
    val book = rowStore.loadBook(id)
    Description(id, book.name, size(book), unknownNarrator = true)
  }

  def narration(id: Vid): Option[Contents] = {
    @scala.annotation.tailrec
    def recurse(content: List[ReadableContent],
                art: List[SharedImage],
                chap: List[ChapterPos],
                c: Int)
    : (List[SharedImage], List[ChapterPos]) = {
      content match {
        case Nil => (art, chap)
        case h :: t =>
          h match {
            case Article(ImageRef(ref, origin, data), blob) =>
              val article = SharedImage(origin = origin.uriString, blob, data)
              recurse(t, article :: art, if (chap.isEmpty) List(ChapterPos("", 0)) else chap, c + 1)
            case Chapter(name) => recurse(t, art, ChapterPos(name, c) :: chap, c)
          }
      }
    }

    // load the book in an order suitable for viewing
    val pages = linearizedContents(rowStore.loadBook(id))
    if (pages.isEmpty) None
    else {
      val (articles, chapters) = recurse(pages, Nil, Nil, 0)
      Some(Contents(Gallery.fromSeq(articles.reverse), chapters))
    }
  }

  def narrations(): Set[Description] = {
    val books = rowStore.allVids().map {description}
    var known = books.map(d => d.id -> d).toMap
    val nars = narratorCache.all.map { n =>
      known.get(n.id) match {
        case None => Description(n.id, n.name, 0, unknownNarrator = false)
        case Some(desc) =>
          known = known - n.id
          desc.copy(unknownNarrator = false)
      }
    }
    nars ++ known.values
  }


  def size(book: Book): Int = linearizedContents(book).count {_.isInstanceOf[Article]}

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
