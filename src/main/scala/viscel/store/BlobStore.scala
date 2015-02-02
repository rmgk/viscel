package viscel.store

import java.nio.file.{Path, Files}
import java.security.MessageDigest

import viscel.Viscel

object BlobStore {

	val digester = MessageDigest.getInstance("SHA1")

	def sha1hex(b: Array[Byte]) = Predef.wrapByteArray(digester.digest(b)).map { h => f"$h%02x" }.mkString

	def write(sha1: String, bytes: Array[Byte]): Unit = {
		val path = Viscel.basepath.resolve(hashToFilename(sha1))
		Files.createDirectories(path.getParent)
		Files.write(path, bytes)
	}

	def write(bytes: Array[Byte]): String = {
		val sha1 = sha1hex(bytes)
		write(sha1, bytes)
		sha1
	}

	def hashToFilename(h: String): String = new StringBuilder(h).insert(2, '/').insert(0, "./cache/").toString()

	def hashToPath(h: String): Path = Viscel.basepath.resolve(hashToFilename(h))

}
