package viscel.server

import viscel.scribe.{Article, Chapter, ImageRef, LinearizeContents, ReadableContent, RowStore}
import viscel.shared.{ChapterPos, Contents, Description, Gallery, SharedImage, Vid}
import viscel.store.{DescriptionCache, NarratorCache}

class ContentLoader(narratorCache: NarratorCache, rowStore: RowStore, descriptionCache: DescriptionCache) {


  private def description(id: Vid): Description = descriptionCache.getOrElse(id) {
    val book = rowStore.load(id)
    Description(id, book.name, LinearizeContents.size(book), unknownNarrator = true)
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
    val pages = LinearizeContents.linearizedContents(rowStore.load(id))
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

}
