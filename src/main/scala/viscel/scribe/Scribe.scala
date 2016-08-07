package viscel.scribe

import java.nio.file.{Files, Path}

import viscel.narration.Narrator
import viscel.shared.Description

import scala.collection.JavaConverters._
import scala.collection.immutable.HashSet
import scala.language.implicitConversions

class Scribe(basedir: Path, configdir: Path) {

	val descriptionpath = configdir.resolve("descriptions.json")

	var descriptionCache: Map[String, Description] =
		Json.load[Map[String, Description]](descriptionpath).getOrElse(Map())

	def invalidateSize(book: Book): Unit = synchronized {
		descriptionCache = descriptionCache - book.id
		Json.store[Map[String, Description]](descriptionpath, descriptionCache)
	}

	def findOrCreate(narrator: Narrator): Book = find(narrator.id).getOrElse {create(narrator)}

	def create(narrator: Narrator): Book = {
		val path = basedir.resolve(narrator.id)
		if (Files.exists(path) && Files.size(path) > 0) throw new IllegalStateException(s"already exists $path")
		Json.store(path, narrator.name)
		new Book(path, this)
	}

	def find(id: String): Option[Book] = synchronized {
		val path = basedir.resolve(id)
		if (Files.isRegularFile(path) && Files.size(path) > 0) {
			val book = new Book(path, this)
			Some(book)

		}
		else None
	}

	def allDescriptions(): List[Description] = synchronized {
		Files.list(basedir).iterator().asScala.filter(Files.isRegularFile(_)).map { path =>
			val id = path.getFileName.toString
			descriptionCache.getOrElse(id, {
				val book = find(id).get
				val desc = Description(id, book.name, book.size(), archived = true)
				descriptionCache = descriptionCache.updated(id, desc)
				Json.store[Map[String, Description]](descriptionpath, descriptionCache)
				desc
			})
		}.toList
	}

	def allBlobsHashes(): Set[String] = {
		Files.list(basedir).iterator().asScala.filter(Files.isRegularFile(_)).flatMap { path =>
			val id = path.getFileName.toString
			val book = find(id).get
			book.allBlobs().map(_.blob.sha1)
		}.to[HashSet]
	}


}
