package viscel.store

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}

import argonaut.Argonaut._
import argonaut.{CodecJson, Parse}
import org.scalactic._

import scala.collection.JavaConverters._
import scala.collection.immutable.Map

final case class User(id: String, password: String, bookmarks: Map[String, Int]) {
	def setBookmark(collection: String, position: Int): User = copy(bookmarks = bookmarks.updated(collection, position))
	def removeBookmark(collection: String): User = copy(bookmarks = bookmarks.-(collection))
}

object User {

	implicit def userCodec: CodecJson[User] = casecodec3(User.apply, User.unapply)("id", "password", "bookmarks")

	val charset = Charset.forName("UTF-8")

	def path(id: String): Path = Paths.get(s"users/$id.json")

	def load(id: String): User Or ErrorMessage = synchronized {
		try {
			val jsonString = Files.readAllLines(path(id), charset).asScala.mkString("\n")
			Or.from(Parse.decodeEither[User](jsonString).toEither)
		}
		catch { case e: IOException => Bad(e.getMessage) }
	}

	def store(user: User): Unit = synchronized {
		val jsonBytes = user.asJson.spaces2.getBytes(charset)
		val p = path(user.id)
		Files.createDirectories(p.getParent)
		Files.write(p, jsonBytes)
	}
}
