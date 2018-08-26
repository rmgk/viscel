package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files.File
import viscel.scribe.ScribePicklers._
import viscel.shared.Log.{Scribe => Log}


case class Book(id: String,
                name: String,
                pages: Map[Vurl, PageData],
                blobs: Map[Vurl, BlobData],
               ) {

  def addBlob(blob: BlobData): Book = copy(blobs = blobs.updated(blob.ref, blob))

  def addPage(entry: PageData): (Book, Option[Int]) = {
    val oldPage = pages.get(entry.ref)
    if (oldPage.isEmpty || oldPage.get.differentContent(entry)) {
      val newBook = copy(pages = pages.updated(entry.ref, entry))
      val oldCount = oldPage.fold(0)(_.articleCount)
      (newBook, Some(entry.articleCount - oldCount))
    }
    else (this, None)
  }

  def beginning: Option[PageData] = pages.get(Vurl.entrypoint)
  def hasPage(ref: Vurl): Boolean = pages.contains(ref)
  def hasBlob(ref: Vurl): Boolean = blobs.contains(ref)

  def allBlobs(): Iterator[BlobData] = blobs.valuesIterator
  def allPages(): Iterator[PageData] = pages.valuesIterator

}

object Book {
  def load(path: Path): Book = {

    Log.info(s"reading $path")

    val lines = File(path).lineIterator(StandardCharsets.UTF_8)
    val name = io.circe.parser.decode[String](lines.next()).toTry.get

    val entryList = lines.zipWithIndex.map { case (line, nr) =>
      io.circe.parser.decode[ScribeDataRow](line) match {
        case Right(s) => s
        case Left(t) =>
          Log.error(s"Failed to decode $path:${nr + 2}: $line")
          throw t
      }
    }.toList

    val pages: Map[Vurl, PageData] = entryList.collect{ case pd@PageData(ref, _, date, contents) => (ref, pd) }(scala.collection.breakOut)
    val blobs: Map[Vurl, BlobData] = entryList.collect{ case bd@BlobData(ref, loc, date, blob) => (ref, bd) }(scala.collection.breakOut)


    val id = path.getFileName.toString
    new Book(id, name, pages, blobs)
  }
}
