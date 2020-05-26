package viscel.store

import viscel.netzi.VRequest
import viscel.shared.DataRow.Link
import viscel.shared.{DataRow, Log, Vid, Vurl}

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

  def reachable(): Set[Vurl] = {
    def rec(check: List[Vurl], acc: Set[Vurl]): Set[Vurl] = {
      check match {
        case Nil => acc
        case head :: tail =>
          pages.get(head) match {
            case Some(page) =>
              val inner = page.contents.collect{ case l : Link if !acc.contains(l.ref) => l.ref }
              rec(inner reverse_:::  tail, acc + head)
            case None =>
              Log.Tool.warn(s"$id has no $head")
              rec(tail, acc + head)

          }
      }
    }
    rec(List(Book.entrypoint), Set.empty)
  }


  /** Add a new page to this book.
    *
    * @return Book if updated */
  def addPage(newEntry: DataRow): Option[Book]= {
    pages.get(newEntry.ref) match {
      case Some(oldPage) if (newEntry.updates(oldPage)) =>
        Some(copy(pages = pages.updated(newEntry.ref, newEntry)))
      case _                           => None
    }
  }

}

object Book {
  val entrypoint: Vurl = Vurl("viscel:///initial")

  def fromEntries(id: Vid,
                  name: String,
                  entryList: Iterable[DataRow])
  : Book = {
    val pages: Map[Vurl, DataRow] = entryList.map(pd  => (pd.ref, pd)).toMap
    Book(id, name, pages)
  }
}
