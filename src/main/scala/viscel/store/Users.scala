package viscel.store

import java.io.IOException
import java.nio.file.{Files, Path}

import org.scalactic.Accumulation._
import org.scalactic.{Bad, ErrorMessage, Every, One, Or}
import viscel.Viscel
import viscel.shared.JsonCodecs.{case4RW, stringMapR, stringMapW}
import viscel.shared.ReaderWriter

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

object Users {

	val usersDir: Path = Viscel.basepath.resolve(s"users")

	implicit val userRW: ReaderWriter[User] = case4RW(User.apply, User.unapply)("id", "password", "admin", "bookmarks")

	def all(): List[User] Or Every[ErrorMessage] = try {
		if (!Files.isDirectory(usersDir)) Nil
		else Files.newDirectoryStream(usersDir, "*.json").asScala.map(load(_).accumulating).toList.combined
	}
	catch {
		case e: IOException => Bad(One(e.getMessage))
	}

	def path(id: String): Path = usersDir.resolve(s"$id.json")

	def load(id: String): User Or ErrorMessage = load(path(id))

	def load(p: Path): User Or ErrorMessage = Json.load[User](p).badMap(e => e.getMessage)

	def store(user: User): Unit = Json.store(path(user.id), user)
}
