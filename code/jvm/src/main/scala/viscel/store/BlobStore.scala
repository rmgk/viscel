package viscel.store

import java.nio.file.{Files, Path}
import java.security.MessageDigest

import viscel.Services
import viscel.shared.{DataRow, Log}

import scala.jdk.CollectionConverters._
import scala.collection.mutable

class BlobStore(val blobdir: Path) {

  def write(sha1: String, bytes: Array[Byte]): Unit = {
    val path = hashToPath(sha1)
    Files.createDirectories(path.getParent)
    if (!Files.exists(path)) {
      Log.Store.trace(s"storing $path")
      Files.write(path, bytes)
    }
  }

  def write(bytes: Array[Byte]): String = {
    val sha1 = BlobStore.sha1hex(bytes)
    write(sha1, bytes)
    sha1
  }

  def hashToPath(h: String): Path = blobdir.resolve(h.substring(0, 2)).resolve(h.substring(2))

}

object BlobStore {
  private val sha1digester: MessageDigest = MessageDigest.getInstance("SHA-1")
  def sha1(b: Array[Byte]): Array[Byte]   = sha1digester.clone().asInstanceOf[MessageDigest].digest(b)
  def sha1hex(b: Array[Byte]): String     = sha1(b).map { h => f"$h%02x" }.mkString

  val Log = viscel.shared.Log.Tool

  def cleanBlobDirectory(services: Services): Unit = {
    Log.info(s"scanning all blobs …")
    val blobsHashesInDB: Set[String] = {
      services.rowStore.allVids().flatMap { id =>
        val (name, rows) = services.rowStore.load(id)
        rows.iterator.flatMap(dr => dr.contents.iterator).collect { case l: DataRow.Blob => l.sha1 }
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
