package viscel.scribe

import viscel.narration.Narrator
import viscel.shared.{Description, Vid}
import viscel.store.DescriptionCache

import scala.collection.immutable.HashSet



class Scribe(rowStore: RowStore, descriptionCache: DescriptionCache) {

  /** creates a new book able to add new pages */
  def loadOrCreate(narrator: Narrator): Book = synchronized{
    find(narrator.id).getOrElse(create(narrator))
  }

  private def create(narrator: Narrator): Book = synchronized {
    rowStore.createNew(narrator)
    Book(narrator.id, narrator.name)
  }

  def addImageTo(book: Book, blobData: BlobData): Book = synchronized {
    rowStore.append(book.id, blobData)
    book.addBlob(blobData)
  }

  def addPageTo(book: Book, pageData: PageData): Book = synchronized {
    val (newBook, written) = book.addPage(pageData)
    written match {
      case None =>
      case Some(i) =>
        descriptionCache.updateSize(newBook.id, i)
        rowStore.append(newBook.id, pageData)
    }
    newBook
  }


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
    rowStore.allVids().flatMap{ id =>
      val book = find(id).get
      book.allBlobs().map(_.blob.sha1)
    }.to[HashSet]
  }




}
