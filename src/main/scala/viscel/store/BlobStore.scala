package viscel.store

import java.nio.file.{Files, Path}
import java.security.MessageDigest

import viscel.shared.Log

class BlobStore(blobdir: Path) {


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
  def sha1(b: Array[Byte]): Array[Byte] = sha1digester.clone().asInstanceOf[MessageDigest].digest(b)
  def sha1hex(b: Array[Byte]): String = sha1(b).map { h => f"$h%02x" }.mkString
}
