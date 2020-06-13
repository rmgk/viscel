package viscel.server

import viscel.shared.{Contents, Description, Log, Vid}
import viscel.store.{RowStoreV4, _}

class ContentLoader(narratorCache: NarratorCache,
                    rowStore: RowStoreV4,
                    descriptionCache: DescriptionCache) {

  /** load the book in an order suitable for viewing */
  def contents(id: Vid): Option[Contents] = {
    try {
      val book  = rowStore.loadBook(id)
      BookToContents.contents(book)
    } catch {
      case e: IllegalStateException => None
      case other: Throwable         =>
        Log.Server.warn(s"exception while loading book: ${other.getMessage}")
        None
    }
  }


  def descriptions(): Map[Vid, Description] = {
    Log.Server.debug(s"requesting descriptions")
    val stored   : Map[Vid, Description] = rowStore.allVids().map { id => id -> description(id) }.toMap
    val narrators: Map[Vid, Description] = narratorCache.all.map { n =>
      stored.get(n.id) match {
        case None       => n.id -> Description(n.name, 0, linked = true,
                                               timestamp = System.currentTimeMillis())
        case Some(desc) => n.id -> desc.copy(linked = true)
      }
    }.toMap

    val res = stored ++ narrators
    Log.Server.debug(s"found ${res.size} descriptions")
    res
  }

  private def description(id: Vid): Description = {
    Log.Server.trace(s"requesting description for $id")
    descriptionCache.getOrElse(id) {
      Log.Server.trace(s"computing description for $id")
      val book = rowStore.loadBook(id)
      Description(book.name, BookToContents.size(book), linked = false, timestamp = System.currentTimeMillis())
    }
  }


}
