package viscel.store.v4

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files.File
import com.github.plokhotnyuk.jsoniter_scala.core._
import io.circe.syntax._
import viscel.narration.Narrator
import viscel.shared.CirceCodecs._
import viscel.shared.Log.{Store => Log}
import viscel.shared.{DataRow, JsoniterCodecs, Vid}
import viscel.store.{Book, JsoniterStorage}


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
    if (!f.exists || f.size <= 0) JsoniterStorage.store(f.path, name)(JsoniterCodecs.StringRw)
    new RowAppender(f)
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


}

class RowAppender(file: File) {
  def append(row: DataRow): Unit = {
    Log.trace(s"Store $row into $file")
    file.appendLine(row.asJson.noSpaces)(charset = StandardCharsets.UTF_8)
  }
}
