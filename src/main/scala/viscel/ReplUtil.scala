package viscel

import java.nio.file.Files

import viscel.store.BlobStore

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.immutable.HashSet
import scala.collection.mutable

object MimeUtil {
  def mimeToExt(mime: String, default: String = ""): String = mime match {
    case "image/jpeg" => "jpg"
    case "image/gif" => "gif"
    case "image/png" => "png"
    case "image/bmp" => "bmp"
    case _ => default
  }
}

class ReplUtil(services: Services) {

  val Log = viscel.shared.Log.Tool

  def cleanBlobDirectory(): Unit = {
    Log.info(s"scanning all blobs …")
    val blobsHashesInDB = {
      services.rowStore.allVids().flatMap { id =>
        val book = services.rowStore.loadBook(id)
        book.allBlobs().map(_.sha1)
      }.toSet
    }
    Log.info(s"scanning files …")
    val bsn = new BlobStore(services.basepath.resolve("blobbackup"))

    val seen = mutable.HashSet[String]()

    Files.walk(services.blobdir).iterator().asScala.filter(Files.isRegularFile(_)).foreach { bp =>
      val sha1path = s"${bp.getName(bp.getNameCount - 2)}${bp.getFileName}"
      //val sha1 = blobs.sha1hex(Files.readAllBytes(bp))
      //if (sha1path != sha1) Log.warn(s"$sha1path did not match")
      seen.add(sha1path)
      if (!blobsHashesInDB.contains(sha1path)) {
        val newpath = bsn.hashToPath(sha1path)
        Log.info(s"moving $bp to $newpath")
        Files.createDirectories(newpath.getParent)
        Files.move(bp, newpath)
      }
    }
    blobsHashesInDB.diff(seen).foreach(sha1 => Log.info(s"$sha1 is missing"))
  }


}


