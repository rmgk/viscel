package viscel.scribe.store

import java.nio.file.{Files, Path}
import java.security.MessageDigest

class BlobStore(basedir: Path) {

	val digester = MessageDigest.getInstance("SHA1")

	def sha1hex(b: Array[Byte]) = Predef.wrapByteArray(digester.digest(b)).map { h => f"$h%02x" }.mkString

	def write(sha1: String, bytes: Array[Byte]): Unit = {
		val path = hashToPath(sha1)
		Files.createDirectories(path.getParent)
		Files.write(path, bytes)
	}

	def write(bytes: Array[Byte]): String = {
		val sha1 = sha1hex(bytes)
		write(sha1, bytes)
		sha1
	}

	def hashToPath(h: String): Path = basedir.resolve(h.substring(0, 2)).resolve(h.substring(2))

}
