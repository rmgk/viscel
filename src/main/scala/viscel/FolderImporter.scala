package viscel

import better.files._
import viscel.MimeUtil.mimeToExt
import viscel.netzi.Vurl
import viscel.shared.Vid
import viscel.store._
import viscel.store.v4.{DataRow, RowStoreV4}

class FolderImporter(blobStore: BlobStore, rowStore: RowStoreV4, descriptionCache: DescriptionCache) {

  private val Log = viscel.shared.Log.Tool


  def importFolder(path: String, vid: Vid, nname: String): Unit = {

    Log.info(s"try to import $vid($nname) form $path")

    val sortedFiles: IndexedSeq[File] = File(path).walk().toArray.sortBy(_.pathAsString)

    val story: List[DataRow.Content] = sortedFiles.iterator.flatMap { p =>
      if (p.isDirectory) Some(DataRow.Chapter(name = p.name))
      else if (p.isRegularFile) {
        val mime = p.contentType.getOrElse("")
        if (mimeToExt(mime, default = "") == "") None
        else {
          Log.info(s"processing $p")
          val sha1 = blobStore.write(p.byteArray)
          val blob = DataRow.Blob(sha1, mime)
          Some(blob)
        }
      }
      else None
    }.toList


    val appender = rowStore.open(vid, nname)
    appender.append(DataRow(Book.entrypoint, contents = story))
    descriptionCache.invalidate(vid)
  }
}
