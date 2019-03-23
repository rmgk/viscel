package viscel.store.v4

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files.File
import io.circe.syntax._
import viscel.narration.Narrator
import viscel.shared.Log.{Store => Log}
import viscel.shared.Vid
import viscel.store.Json
import viscel.store.v4.V4Codecs._

class RowStoreV4 (db4dir: Path) {

    val base = File(db4dir)

    def open(narrator: Narrator): RowAppender = synchronized {
      open(narrator.id, narrator.name)
    }

    def open(id: Vid, name: String): RowAppender = synchronized{
      val f = base / id.str
      if (!f.exists || f.size <= 0) Json.store(f.path, name)
      new RowAppender(f)
    }


    def load(id: Vid): (String, List[DataRow]) = synchronized {
      Log.info(s"loading $id")

      val f = base / id.str

      if (!f.isRegularFile || f.size == 0)
        throw new IllegalStateException(s"$f does not contain data")
      else {
        val lines = f.lineIterator(StandardCharsets.UTF_8)
        val name = io.circe.parser.decode[String](lines.next()).toTry.get

        val dataRows = lines.zipWithIndex.map { case (line, nr) =>
          io.circe.parser.decode[DataRow](line) match {
            case Right(s) => s
            case Left(t)  =>
              Log.error(s"Failed to decode $id:${nr + 2}: $line")
              throw t
          }
        }.toList
        (name, dataRows)
      }
    }

  }

  class RowAppender(file: File) {
    def append(row: DataRow): Unit = {
      Log.trace(s"Store $row into $file")
      file.appendLine(row.asJson.noSpaces)(charset = StandardCharsets.UTF_8)
    }

}
