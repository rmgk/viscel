package viscel.scribe

import java.nio.file.{Files, Path}

import viscel.narration.Narrator
import viscel.shared.Description

import scala.collection.JavaConverters._
import scala.collection.immutable.HashSet
import io.circe.generic.auto._

class Scribe(basedir: Path, configdir: Path) {

	private val descriptionpath: Path = configdir.resolve("descriptions.json")

	private var descriptionCache: Map[String, Description] =
		Json.load[Map[String, Description]](descriptionpath).getOrElse(Map())

	def invalidateCache(id: String): Unit = synchronized {
		descriptionCache = descriptionCache - id
	}

	def invalidateSize(book: Book, sizeDelta: Int): Unit = synchronized {
		descriptionCache.get(book.id) match {
			case None => descriptionCache = descriptionCache.updated(book.id, description(book.id))
			case Some(desc) => descriptionCache = descriptionCache.updated(book.id, desc.copy(size = desc.size + sizeDelta))
		}
		Json.store[Map[String, Description]](descriptionpath, descriptionCache)
	}

	/** creates a new book able to add new pages */
	def findOrCreate(narrator: Narrator): Book = synchronized(find(narrator.id).getOrElse {create(narrator)})

	private def create(narrator: Narrator): Book = synchronized {
		val path = basedir.resolve(narrator.id)
		if (Files.exists(path) && Files.size(path) > 0) throw new IllegalStateException(s"already exists $path")
		Json.store(path, narrator.name)
		Book.load(path, this)
	}

	/** returns the list of pages of an id, an empty list if the id does not exist
		* used by the server to inform the client */
	def findPages(id: String): List[ReadableContent] = {
		find(id).map(_.pages()).getOrElse(Nil)
	}

	private def find(id: String): Option[Book] = synchronized {
		val path = basedir.resolve(id)
		if (Files.isRegularFile(path) && Files.size(path) > 0) {
			val book = Book.load(path, this)
			Some(book)

		}
		else None
	}

	def allDescriptions(): List[Description] = synchronized {
		Files.list(basedir).iterator().asScala.filter(Files.isRegularFile(_)).map { path =>
			val id = path.getFileName.toString
			description(id)
		}.toList
	}

	private def description(id: String): Description = {
		descriptionCache.getOrElse(id, {
			val book = find(id).get
			val desc = Description(id, book.name, book.size(), archived = true)
			descriptionCache = descriptionCache.updated(id, desc)
			Json.store[Map[String, Description]](descriptionpath, descriptionCache)
			desc
		})
	}

	/** helper method for database clean */
	def allBlobsHashes(): Set[String] = {
		Files.list(basedir).iterator().asScala.filter(Files.isRegularFile(_)).flatMap { path =>
			val id = path.getFileName.toString
			val book = find(id).get
			book.allBlobs().map(_.blob.sha1)
		}.to[HashSet]
	}


}
