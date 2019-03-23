package viscel.store

import java.nio.file.Path

import better.files.File
import viscel.shared.Log.{Store => Log}
import viscel.shared.Vid
import viscel.store.v3.{OldRowStore, RowStoreTransition}
import viscel.store.v4.RowStoreV4

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
