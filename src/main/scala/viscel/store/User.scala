package viscel.store

import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}

import org.scalactic.{Bad, ErrorMessage, Good, Or}
import upickle.{Reader, Writer}
import viscel.shared.JsonCodecs.{case3RW, stringMapR, stringMapW}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.immutable.Map

final case class User(id: String, password: String, bookmarks: Map[String, Int]) {
	def setBookmark(collection: String, position: Int): User = copy(bookmarks = bookmarks.updated(collection, position))
	def removeBookmark(collection: String): User = copy(bookmarks = bookmarks - collection)
}

object User {


	implicit val (userR, userW): (Reader[User], Writer[User]) = case3RW(User.apply, User.unapply)("id", "password", "bookmarks")

	val charset = Charset.forName("UTF-8")

	def path(id: String): Path = Paths.get(s"users/$id.json")

	def load(id: String): User Or ErrorMessage = synchronized {
		try {
			val jsonString = Files.readAllLines(path(id), charset).asScala.mkString("\n")
			Good(upickle.read[User](jsonString))
		}
		catch { case e: Exception => Bad(e.getMessage) }
	}

	def store(user: User): Unit = synchronized {
		val jsonBytes = upickle.write(user).getBytes(charset)
		val p = path(user.id)
		Files.createDirectories(p.getParent)
		Files.write(p, jsonBytes)
	}
}
