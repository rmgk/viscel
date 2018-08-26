package viscel.scribe

import viscel.shared.{Description, Vid}
import viscel.store.DescriptionCache

import scala.collection.immutable.HashSet


class Scribe(rowStore: RowStore, descriptionCache: DescriptionCache) {

  private def find(id: Vid): Option[Book] = Book.load(id, rowStore)

  /** returns the list of pages of an id, an empty list if the id does not exist
    * used by the server to inform the client */
  def loadLinearizedContents(id: Vid): List[ReadableContent] = {
    find(id).map(LinearizeContents.linearizedContents).getOrElse(Nil)
  }

  def allDescriptions(): List[Description] = synchronized {
    rowStore.allVids().map {description}
  }

  private def description(id: Vid): Description = descriptionCache.getOrElse(id) {
    val book = find(id).get
    Description(id, book.name, LinearizeContents.size(book), unknownNarrator = true)
  }


  /** helper method for database clean */
  def allBlobsHashes(): Set[String] = {
    rowStore.allVids().flatMap { id =>
      val book = find(id).get
      book.allBlobs().map(_.blob.sha1)
    }.to[HashSet]
  }


}
