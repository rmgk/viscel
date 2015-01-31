package viscel.store

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}

import org.scalactic.Accumulation._
import org.scalactic.{Bad, ErrorMessage, Every, Good, One, Or}
import viscel.shared.JsonCodecs.{case4RW, stringMapR, stringMapW}
import viscel.shared.ReaderWriter

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

object Users {

	val usersDir: Path = Paths.get(s"users")

	implicit val userRW: ReaderWriter[User] = case4RW(User.apply, User.unapply)("id", "password", "admin", "bookmarks")

	def all(): List[User] Or Every[ErrorMessage] = try {
		Files.newDirectoryStream(usersDir, "*.json").asScala.map(load(_).accumulating).toList.combined
	}
	catch {
		case e: IOException => Bad(One(e.getMessage))
	}

	def path(id: String): Path = usersDir.resolve(s"$id.json")

	def load(id: String): User Or ErrorMessage = load(path(id))

	def load(p: Path): User Or ErrorMessage = synchronized {
		try {
			val jsonString = Files.readAllLines(p, UTF_8).asScala.mkString("\n")
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
