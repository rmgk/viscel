package viscel.store

import viscel.netzi.{VRequest, Vurl}
import viscel.shared.Vid
import viscel.store.v4.DataRow

case class Book(id: Vid,
                name: String,
                pages: Map[Vurl, DataRow] = Map()
               ) {

  def notJustBlob(loc: Vurl): Boolean =
    pages.get(loc).fold(true){dr =>
      dr.contents.isEmpty ||  dr.contents.exists(c => !c.isInstanceOf[DataRow.Blob])}

  def beginning: Option[DataRow] = pages.get(Book.entrypoint)
  def hasPage(ref: Vurl): Boolean = pages.contains(ref)

  def allBlobs(): Iterator[DataRow.Blob] =
    pages.valuesIterator.flatMap(dr => dr.contents.iterator).collect{case l : DataRow.Blob => l}

  def allLinks: Iterator[VRequest] = {
    pages.valuesIterator.flatMap{ dr =>
      dr.contents.iterator.collect{case l : DataRow.Link => VRequest(l.ref, l.data, dr.loc.orElse(Some(dr.ref)))}
    }
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
  val entrypoint: Vurl = Vurl("viscel:///initial")

  def fromEntries(id: Vid,
                  name: String,
                  entryList: Iterable[DataRow])
  : Book = {
    val pages: Map[Vurl, DataRow] = entryList.map(pd  => (pd.ref, pd))(scala.collection.breakOut)
    Book(id, name, pages)
  }
}
