package viscel.store

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

import better.files.File
import viscel.shared.Log.{Store => Log}
import viscel.shared.{Blob, Vid}
import viscel.store.CustomPicklers._
import viscel.store.v4.{DataRow, RowStoreV4}

class StoreManager(db3dir: Path, db4dir: Path) {


  val oldBase  = File(db3dir)
  val newBase  = File(db4dir)
  val newStore = new RowStoreV4(db4dir)
  val oldStore = new OldRowStore(db3dir)
  def transition(): RowStoreV4 = {
    if (oldBase.isDirectory && newBase.isEmpty) {
      Log.info(s"Updating `$oldBase` to `$newBase`, do not abort. " +
               s"If something fails, delete `$newBase` to try again.")
      oldBase.list(_.isRegularFile, 1).foreach { file =>
        val id = Vid.from(file.name)
        val (name, entries) = oldStore.loadOld(id)
        val apppender = newStore.open(id, name)
        entries.foreach { entry =>
          apppender.append(RowStoreTransition.transform(entry))
        }
      }
    }
    newStore
  }
}

class OldRowStore(oldBase: Path) {

  def loadOld(id: Vid): (String, List[ScribeDataRow]) = synchronized {
    Log.info(s"loading $id")

    val f = File(oldBase) / id.str

    if (!f.isRegularFile || f.size == 0) throw new IllegalStateException(s"$f does not contain a book")
    else {
      val lines = f.lineIterator(StandardCharsets.UTF_8)
      val name = io.circe.parser.decode[String](lines.next()).toTry.get

      val entryList = lines.zipWithIndex.map { case (line, nr) =>
        io.circe.parser.decode[ScribeDataRow](line) match {
          case Right(s) => s
          case Left(t)  =>
            Log.error(s"Failed to decode $id:${nr + 2}: $line")
            throw t
        }
      }.toList
      (name, entryList)
    }
  }
}

object RowStoreTransition {
  def transform(row: ScribeDataRow): DataRow = {
    val (ref, loc, date, newContents) = row match {
      case PageData(ref, loc, date, contents) =>
        (ref, loc, date, contents.map(transformContent))

      case BlobData(ref, loc, date, Blob(sha1, mime)) =>
        (ref, loc, date, List(DataRow.Blob(sha1, mime)))
    }

    DataRow(ref,
            if (ref != loc) Some(loc) else None,
            if (date.isBefore(Instant.now()
                              .minus(1, ChronoUnit.MINUTES))) Some(date) else None,
            None,
            newContents)
  }
  def transformContent(contents: WebContent): DataRow.Content = contents match {
    case Chapter(name)           => DataRow.Chapter(name)
    case ImageRef(ref, _, data)  => DataRow.Link(ref,
                                                 data.toList.flatMap(p => List(p._1, p._2)))
    case Link(ref, policy, data) => DataRow.Link(ref, policy.toString :: data)
  }
}
