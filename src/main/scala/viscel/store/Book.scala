package viscel.store

import viscel.shared.Vid

case class Book(id: Vid,
                name: String,
                pages: Map[Vurl, PageData] = Map(),
                blobs: Map[Vurl, BlobData] = Map(),
               ) {
  def beginning: Option[PageData] = pages.get(Vurl.entrypoint)
  def hasPage(ref: Vurl): Boolean = pages.contains(ref)
  def hasBlob(ref: Vurl): Boolean = blobs.contains(ref)

  def allBlobs(): Iterator[BlobData] = blobs.valuesIterator
  def allPages(): Iterator[PageData] = pages.valuesIterator

  def addBlob(blob: BlobData): Book = copy(blobs = blobs.updated(blob.ref, blob))

  /** Add a new page to this book.
    * @return New book and an estimate of the increased size, or None if the book is unchanged. */
  def addPage(entry: PageData): (Book, Option[Int]) = {
    val oldPage = pages.get(entry.ref)
    if (oldPage.isEmpty || oldPage.get.differentContent(entry)) {
      val newBook = copy(pages = pages.updated(entry.ref, entry))
      val oldCount = oldPage.fold(0)(_.articleCount)
      (newBook, Some(entry.articleCount - oldCount))
    }
    else (this, None)
  }

}

object Book {
  def fromEntries(id: Vid,
                  name: String,
                  entryList: Seq[ScribeDataRow])
  : Book = {
    val pages: Map[Vurl, PageData] = entryList.collect {
      case pd@PageData(ref, _, date, contents) => (ref, pd)
    }(scala.collection.breakOut)

    val blobs: Map[Vurl, BlobData] = entryList.collect {
      case bd@BlobData(ref, loc, date, blob) => (ref, bd)
    }(scala.collection.breakOut)

    Book(id, name, pages, blobs)
  }
}
