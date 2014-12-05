package viscel.store

import java.nio.file.{Files, Paths}

object Cache {

	def write(sha1: String, bytes: Array[Byte]) = {
		val path = Paths.get(hashToFilename(sha1))
		Files.createDirectories(path.getParent)
		Files.write(path, bytes)
	}

	def hashToFilename(h: String): String = new StringBuilder(h).insert(2, '/').insert(0, "./cache/").toString()

}
