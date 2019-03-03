package viscel

import java.time.Instant

import better.files._
import viscel.MimeUtil.mimeToExt
import viscel.narration.Narrator
import viscel.narration.interpretation.NarrationInterpretation.Wrapper
import viscel.shared.{Blob, Vid}
import viscel.store._

class FolderImporter(blobStore: BlobStore, rowStore: RowStore, descriptionCache: DescriptionCache) {

  private val Log = viscel.shared.Log.Tool


  def importFolder(path: String, nid: Vid, nname: String): Unit = {

    Log.info(s"try to import $nid($nname) form $path")

    val sortedFiles: IndexedSeq[File] = File(path).walk().toArray.sortBy(_.pathAsString)

    val story: IndexedSeq[ReadableContent] = sortedFiles.flatMap { p =>
      if (p.isDirectory) Some(Chapter(name = p.name))
      else if (p.isRegularFile) {
        val mime = p.contentType.getOrElse("")
        if (mimeToExt(mime, default = "") == "") None
        else {
          Log.info(s"processing $p")
          val sha1 = blobStore.write(p.byteArray)
          val blob = Blob(sha1, mime)
          Some(Article(ImageRef(Vurl.blobPlaceholder(blob), Vurl.blobPlaceholder(blob)),
                       Some(blob)))
        }
      }
      else None
    }

    val webcont: List[WebContent] = story.collect {
      case chap @ Chapter(_) => chap
      case Article(ar, _)    => ar
    }(collection.breakOut)

    val blobs = story.collect {
      case Article(ar, Some(blob)) => BlobData(ar.ref, ar.origin, Instant.now(), blob)
    }

    val narrator = new Narrator {
      override def id: Vid = nid
      override def name: String = nname
      override def archive: List[WebContent] = ???
      override def wrapper: Wrapper = ???
    }
    val id = narrator.id
    val appender = rowStore.open(narrator)
    appender.append(PageData(Vurl.entrypoint, Vurl.entrypoint, Instant.now(), webcont))
    blobs.foreach(appender.append)
    descriptionCache.invalidate(id)
  }
}
