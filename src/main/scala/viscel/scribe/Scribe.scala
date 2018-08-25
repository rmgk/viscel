package viscel.scribe

import java.nio.file.{Files, Path}

import better.files.File
import io.circe.syntax._
import viscel.narration.Narrator
import viscel.scribe.ScribePicklers._
import viscel.shared.Description
import viscel.store.{DescriptionCache, Json}

import scala.collection.JavaConverters._
import scala.collection.immutable.HashSet
import scala.scalajs.niocharset.StandardCharsets


class Scribe(basedir: Path, descriptionCache: DescriptionCache) {

  /** creates a new book able to add new pages */
  def findOrCreate(narrator: Narrator): Book = synchronized(find(narrator.id).getOrElse {create(narrator)})

  private def create(narrator: Narrator): Book = synchronized {
    val path = bookpath(narrator.id)
    if (Files.exists(path) && Files.size(path) > 0) throw new IllegalStateException(s"already exists $path")
    Json.store(path, narrator.name)
    Book.load(path)
  }

  /** returns the list of pages of an id, an empty list if the id does not exist
    * used by the server to inform the client */
  def findPages(id: String): List[ReadableContent] = {
    find(id).map(_.linearizedContents()).getOrElse(Nil)
  }

  private def find(id: String): Option[Book] = synchronized {
    val path = bookpath(id)
    if (Files.isRegularFile(path) && Files.size(path) > 0) {
      val book = Book.load(path)
      Some(book)
    }
    else None
  }

  def allDescriptions(): List[Description] = synchronized {
    Files.list(basedir).iterator().asScala.filter(Files.isRegularFile(_)).map { path =>
      val id = path.getFileName.toString
      description(id)
    }.toList
  }

  private def description(id: String): Description = descriptionCache.getOrElse(id) {
    val book = find(id).get
    Description(id, book.name, book.size(), unknownNarrator = true)
  }


  /** helper method for database clean */
  def allBlobsHashes(): Set[String] = {
    Files.list(basedir).iterator().asScala.filter(Files.isRegularFile(_)).flatMap { path =>
      val id = path.getFileName.toString
      val book = find(id).get
      book.allBlobs().map(_.blob.sha1)
    }.to[HashSet]
  }


  def addImageTo(book: Book, blobData: BlobData): Book = synchronized {
    writeScribeDataRow(book, blobData)
    book.addBlob(blobData)
  }

  def addPageTo(book: Book, pageData: PageData): Book = synchronized {
    val (newBook, written) = book.addPage(pageData)
    written match {
      case None =>
      case Some(i) =>
        descriptionCache.updateSize(book.id, i)
        writeScribeDataRow(book, pageData)
    }
    newBook
  }

  private def writeScribeDataRow(book: Book, blobData: ScribeDataRow) = {
    File(bookpath(book.id)).appendLine(blobData.asJson.noSpaces)(charset = StandardCharsets.UTF_8)
  }

  private def bookpath(id: String) = basedir.resolve(id)

}
