package viscel.store

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files.File
import com.github.plokhotnyuk.jsoniter_scala.core._
import viscel.narration.Narrator
import viscel.shared.DataRow.Link
import viscel.shared.Log.{Store => Log}
import viscel.shared.{DataRow, JsoniterCodecs, Vid}


class RowStoreV4(db4dir: Path) {

  val base = File(db4dir)

  def file(vid: Vid): File = {
    base / vid.str
  }

  def allVids(): List[Vid] = synchronized {
    base.list(_.isRegularFile, 1).map(f => Vid.from(f.name)).toList
  }

  def open(narrator: Narrator): RowAppender = synchronized {
    open(narrator.id, narrator.name)
  }

  def open(id: Vid, name: String): RowAppender = synchronized {
    val f = file(id)
    if (!f.exists || f.size <= 0) JsoniterStorage.writeLine(f, name)(JsoniterCodecs.StringRw)
    new RowAppender(f, this)
  }


  def load(id: Vid): (String, List[DataRow]) = synchronized {
    val start = System.currentTimeMillis()

    val f = file(id)

    if (!f.isRegularFile || f.size == 0)
      throw new IllegalStateException(s"$f does not contain data")
    else {
      val lines = f.lineIterator(StandardCharsets.UTF_8)
      val name  = readFromString[String](lines.next())(JsoniterCodecs.StringRw)

      val dataRows = lines.zipWithIndex.map { case (line, nr) =>
        try readFromString[DataRow](line)(JsoniterCodecs.DataRowRw) catch {
          case t : Throwable  =>
            Log.error(s"Failed to decode $id:${nr + 2}: $line")
            throw t
        }
      }.toList
      Log.info(s"loading $id (${System.currentTimeMillis() - start}ms)")
      (name, dataRows)
    }
  }

  def loadBook(id: Vid): Book = {
    val (name, rows) = try {load(id)} catch {
      case e: Throwable =>
        Log.error(s"loading book $id failed: ${e.getMessage}")
        throw e
    }
    Book.fromEntries(id, name, rows)
  }


  def computeGarbage(): Unit = synchronized {
    allVids().foreach { vid =>
      val (name, entries) = load(vid)
      val book            = Book.fromEntries(vid, name, entries)
      val reachable       = book.reachable()
      val filtered        = entries.filter(dr => reachable.contains(dr.ref) && book.pages(dr.ref) == dr)
      file(vid).delete()
      val appender = open(vid, name)
      filtered.foreach(appender.append)
    }
  }

  def filterSingleLevelMissing(vid: Vid): Unit = synchronized {
    val (name, entries) = load(vid)
    val book            = Book.fromEntries(vid, name, entries)
    val filtered        = entries.filter(dr => dr.contents.forall {
      case Link(ref, _) =>
        val res = book.pages.contains(ref)
        if (!res) Log.warn(s"filtering »${dr.ref}«")
          res
      case other => true
    })
    file(vid).delete()
    val appender = open(vid, name)
    filtered.foreach(appender.append)
  }


}

class RowAppender(file: File, rowStoreV4: RowStoreV4) {
  def append(row: DataRow): Unit = rowStoreV4.synchronized {
    Log.trace(s"Store $row into $file")
    JsoniterStorage.writeLine(file, row)(JsoniterCodecs.DataRowRw)
  }
}
