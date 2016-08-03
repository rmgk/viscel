package viscel.scribe

import java.nio.file.{Files, Path}

import viscel.narration.Narrator

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object Scribe {

	def apply(basedir: Path): Scribe = {

		Files.createDirectories(basedir)
		val bookdir = basedir.resolve("db3/books")
		Files.createDirectories(bookdir)

		val blobs = new BlobStore(basedir.resolve("blobs"))

		new Scribe(bookdir, blobs)
	}

}

class Scribe(val basepath: Path, val blobs: BlobStore) {

	def findOrCreate(narrator: Narrator): Book = find(narrator.id).getOrElse{create(narrator)}

	def create(narrator: Narrator): Book = {
		val path = basepath.resolve(narrator.id)
		if (Files.exists(path)) throw new IllegalStateException(s"already exists $path")
		Json.store(path, narrator.name)
		new Book(path)
	}

	def find(id: String): Option[Book] = {
		val path = basepath.resolve(id)
		if (Files.isRegularFile(path)) Some(new Book(path))
		else None
	}

	def all(): List[Book] = Files.list(basepath).iterator().asScala.filter(Files.isRegularFile(_)).map(new Book(_)).toList

}
