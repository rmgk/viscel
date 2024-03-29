package viscel.store

import viscel.narration.Narrator
import viscel.shared.DataRow.Link
import viscel.shared.Log.Store as Log
import viscel.shared.{DataRow, JsoniterCodecs, Vid}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.given
import scala.util.control.NonFatal

class RowStoreV4(base: Path) {

  def file(vid: Vid): Path = {
    base `resolve` vid.str
  }

  def allVids(): List[Vid] =
    synchronized {
      Files.walk(base, 1).iterator.asScala.filter(p => Files.isRegularFile(p)).map((f: Path) =>
        Vid.from(f.getFileName.toString)
      ).toList
    }

  def open(narrator: Narrator): RowAppender =
    synchronized {
      open(narrator.id, narrator.name)
    }

  def open(id: Vid, name: String): RowAppender =
    synchronized {
      val f = file(id)
      if (!Files.exists(f) || Files.size(f) <= 0) JsoniterStorage.writeLine(f, name)(using JsoniterCodecs.StringRw)
      new RowAppender(f, this)
    }

  def load(id: Vid): (String, List[DataRow]) =
    synchronized {
      val start = System.currentTimeMillis()

      val f = file(id)

      if (!Files.isRegularFile(f) || Files.size(f) == 0)
        throw new IllegalStateException(s"$f does not contain data")
      else {
        try
          val res = DBParser.parse(Files.readAllBytes(f))
          Log.info(s"loading »$id« (${System.currentTimeMillis() - start}ms)")
          res
        catch
          case NonFatal(e) =>
            Log.error(s"loading failed »$id«")
            throw e
      }
    }

  def loadBook(id: Vid): Book = {
    val (name, rows) =
      try { load(id) }
      catch {
        case e: Throwable =>
          Log.error(s"loading book $id failed: ${e.getMessage}")
          throw e
      }
    Book.fromEntries(id, name, rows)
  }

  def computeGarbage(): Unit =
    synchronized {
      allVids().foreach { vid =>
        val (name, entries) = load(vid)
        val book            = Book.fromEntries(vid, name, entries)
        val reachable       = book.reachable()
        val filtered        = entries.filter(dr => reachable.contains(dr.ref) && book.pages(dr.ref) == dr)
        Files.delete(file(vid))
        val appender = open(vid, name)
        filtered.foreach(appender.append)
      }
    }

  def filterSingleLevelMissing(vid: Vid): Unit =
    synchronized {
      val (name, entries) = load(vid)
      val book            = Book.fromEntries(vid, name, entries)
      val filtered = entries.filter(dr =>
        dr.contents.forall {
          case Link(ref, _) =>
            val res = book.pages.contains(ref)
            if (!res) Log.warn(s"filtering »${dr.ref}«")
            res
          case other => true
        }
      )
      Files.delete(file(vid))
      val appender = open(vid, name)
      filtered.foreach(appender.append)
    }

}

class RowAppender(file: Path, rowStoreV4: RowStoreV4) {
  def append(row: DataRow): Unit =
    rowStoreV4.synchronized {
      Log.trace(s"Store $row into $file")
      JsoniterStorage.writeLine(file, row)(using JsoniterCodecs.DataRowRw)
    }
}
