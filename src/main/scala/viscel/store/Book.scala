package viscel.store

import viscel.shared.Vid
import viscel.store.v4.DataRow

case class LinkWithReferer(link: DataRow.Link, referer: Vurl)

case class Book(id: Vid,
                name: String,
                pages: Map[Vurl, DataRow] = Map()
               ) {
  def beginning: Option[DataRow] = pages.get(Vurl.entrypoint)
  def hasPage(ref: Vurl): Boolean = pages.contains(ref)
  def hasBlob(ref: Vurl): Boolean = ???

  def allBlobs(): Iterator[BlobData] = ???

  def unresolvedLinks: List[LinkWithReferer] =
    allLinks.filter(l => !hasPage(l.link.ref)).toList


  def volatileAndEmptyLinks: List[LinkWithReferer] =
    allLinks.filter(_.link.data.contains(Volatile.toString)).toList

  def allLinks: Iterator[LinkWithReferer] = {
    pages.valuesIterator.flatMap(dr => dr.contents.iterator.map(c => (c, dr)))
      .collect{case (l : DataRow.Link, c) => LinkWithReferer(l, c.loc.getOrElse(c.ref)) }
  }

  def addBlob(blob: BlobData): Book = ???

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
