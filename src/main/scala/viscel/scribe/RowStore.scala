package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files.File
import io.circe.syntax._
import viscel.narration.Narrator
import viscel.scribe.ScribePicklers._
import viscel.shared.Log.{Scribe => Log}
import viscel.shared.Vid
import viscel.store.Json

class RowStore(basePath: Path) {

  val base = File(basePath)
  assert(base.isDirectory, "row store base path must be directory")

  def createNew(narrator: Narrator): Unit = {
    val f = base / narrator.id.str
    if (f.exists && f.size > 0) throw new IllegalStateException(s"already exists $f")
    Json.store(f.path, narrator.name)
  }

  def allVids(): List[Vid] = synchronized {
    base.list(_.isRegularFile, 1).map(f => Vid.from(f.name)).toList
  }

  def append(vid: Vid, row: ScribeDataRow): Unit = {
    (base/ vid.str).appendLine(row.asJson.noSpaces)(charset = StandardCharsets.UTF_8)
  }

  def load(id: Vid): Option[(String, List[ScribeDataRow])] = synchronized {
    Log.info(s"loading $id")

    val f = File(basePath)./(id.str)

    if (!f.isRegularFile || f.size == 0) None
    else {
      val lines = f.lineIterator(StandardCharsets.UTF_8)
      val name = io.circe.parser.decode[String](lines.next()).toTry.get

      val entryList = lines.zipWithIndex.map { case (line, nr) =>
        io.circe.parser.decode[ScribeDataRow](line) match {
          case Right(s) => s
          case Left(t) =>
            Log.error(s"Failed to decode $id:${nr + 2}: $line")
            throw t
        }
      }.toList
      Some((name, entryList))
    }
  }
}

