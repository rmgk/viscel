package viscel.store

import viscel.shared.Vid
import viscel.store.v4.DataRow

case class WithReferer(link: DataRow.Link, referer: Vurl)

case class Book(id: Vid,
                name: String,
                pages: Map[Vurl, DataRow] = Map()
               ) {
  def beginning: Option[DataRow] = pages.get(Vurl.entrypoint)
  def hasPage(ref: Vurl): Boolean = pages.contains(ref)
  def hasBlob(ref: Vurl): Boolean = ???

  def allBlobs(): Iterator[DataRow.Blob] =
    allContents.collect{case (l : DataRow.Blob, c) => l}

  def allLinks: Iterator[WithReferer] = {
    allContents.collect{case (l : DataRow.Link, c) => WithReferer(l, c.loc.getOrElse(c.ref)) }
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
