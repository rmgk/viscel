package viscel.store

import viscel.crawl.VRequest
import viscel.shared.Vid
import viscel.store.v4.DataRow

case class Book(id: Vid,
                name: String,
                pages: Map[Vurl, DataRow] = Map()
               ) {
  def beginning: Option[DataRow] = pages.get(Vurl.entrypoint)
  def hasPage(ref: Vurl): Boolean = pages.contains(ref)

  def allBlobs(): Iterator[DataRow.Blob] =
    allContents.collect{case (l : DataRow.Blob, c) => l}

  def allLinks: Iterator[VRequest] = {
    allContents.collect{case (l : DataRow.Link, c) => VRequest(l, Some(c.loc.getOrElse(c.ref))) }
  }

  private def allContents: Iterator[(DataRow.Content, DataRow)] = {
    pages.valuesIterator.flatMap(dr => dr.contents.iterator.map(c => (c, dr)))
  }

  /** Add a new page to this book.
    *
    * @return New book and an estimate of the increased size, or None if the book is unchanged. */
  def addPage(entry: DataRow): (Book, Option[Int]) = {
    val oldPage = pages.get(entry.ref)
    if (oldPage.isEmpty || oldPage.get != entry) {
      val newBook = copy(pages = pages.updated(entry.ref, entry))
      // TODO: compute size difference again
      (newBook, Some(0))
    }
    else (this, None)
  }

}

object Book {
  def fromEntries(id: Vid,
                  name: String,
                  entryList: Iterable[DataRow])
  : Book = {
    val pages: Map[Vurl, DataRow] = entryList.map(pd  => (pd.ref, pd))(scala.collection.breakOut)
    Book(id, name, pages)
  }
}
