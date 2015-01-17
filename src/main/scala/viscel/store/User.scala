package viscel.store

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}

import org.scalactic.{Bad, ErrorMessage, Good, Or}
import viscel.shared.JsonCodecs.{case4RW, stringMapR, stringMapW}
import viscel.shared.ReaderWriter

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.immutable.Map

final case class User(id: String, password: String, isAdmin: Boolean, bookmarks: Map[String, Int]) {
	def setBookmark(collection: String, position: Int): User = copy(bookmarks = bookmarks.updated(collection, position))
	def removeBookmark(collection: String): User = copy(bookmarks = bookmarks - collection)
}

object User {

	implicit val userRW: ReaderWriter[User] = case4RW(User.apply, User.unapply)("id", "password", "admin", "bookmarks")

	def path(id: String): Path = Paths.get(s"users/$id.json")

	def load(id: String): User Or ErrorMessage = synchronized {
		try {
			val jsonString = Files.readAllLines(path(id), UTF_8).asScala.mkString("\n")
			Good(upickle.read[User](jsonString))
		}
		catch { case e: Exception => Bad(e.getMessage) }
	}

	def store(user: User): Unit = synchronized {
		val jsonBytes = upickle.write(user).getBytes(UTF_8)
		val p = path(user.id)
		Files.createDirectories(p.getParent)
		Files.write(p, jsonBytes)
	}
}
