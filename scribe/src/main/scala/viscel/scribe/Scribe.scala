package viscel.scribe

import java.nio.file.{Files, Path}

import viscel.scribe.database.Book
import viscel.scribe.narration.AppendLogEntry
import viscel.scribe.store.BlobStore

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object Scribe {

	def apply(basedir: Path)(implicit r: upickle.default.Reader[AppendLogEntry]): Scribe = {

		Files.createDirectories(basedir)
		val bookdir = basedir.resolve("db3/books")
		Files.createDirectories(bookdir)

		val blobs = new BlobStore(basedir.resolve("blobs"))

		new Scribe(bookdir, blobs)
	}

}

class Scribe(val basepath: Path, val blobs: BlobStore)(implicit r: upickle.default.Reader[AppendLogEntry]) {

	def find(id: String): Option[Book] = {
		val path = basepath.resolve(id)
		if (Files.isRegularFile(path)) Some(new Book(path))
		else None
	}

	def all(): List[Book] = Files.list(basepath).iterator().asScala.filter(Files.isRegularFile(_)).map(new Book(_)).toList

}
