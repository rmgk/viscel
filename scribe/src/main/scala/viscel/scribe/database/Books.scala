package viscel.scribe.database


import java.nio.file.{Files, Path}

import viscel.scribe.narration.AppendLogEntry

import scala.collection.JavaConverters._
import viscel.scribe.store.Json._


class Books(basepath: Path)(implicit r: upickle.default.Reader[AppendLogEntry]) {
	def find(id: String): Option[Book] = {
		val path = basepath.resolve(id)
		if (Files.isRegularFile(path)) Some(new Book(path))
		else None
	}

	def all(): List[Book] = Files.list(basepath).iterator().asScala.filter(Files.isRegularFile(_)).map(new Book(_)).toList
}
