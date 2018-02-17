package vitzen.store

import java.io.IOException
import java.nio.file.{Files, Path}

import io.circe.generic.auto._
import org.scalactic.Accumulation._
import org.scalactic.{Bad, ErrorMessage, Every, Good, One, Or}
import vitzen.logging.Logger

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.immutable.Map

final case class User(id: String, password: String, admin: Boolean)


class Users(log: Logger, usersDir: Path) {


  def all(): List[User] Or Every[ErrorMessage] = try {
    if (!Files.isDirectory(usersDir)) Good(Nil)
    else Files.newDirectoryStream(usersDir, "*.json").asScala.map(load(_).accumulating).toList.combined
  }
  catch {
    case e: IOException => Bad(One(e.getMessage))
  }

  def getOrAddFirstUser(name: String, orElse: => User): Option[User] = {
    userCache.get(name).orElse(
      (load(name) match {
        case Good(g) => Some(g)
        case Bad(e) =>
          log.warn(s"could not open user $name: $e")
          val firstUser = all().fold(_.isEmpty, _ => false)
          if (firstUser) {
            log.info(s"create initial user: $name")
            Some(orElse)
          } else None
      }).map(userUpdate))
  }

  private def path(id: String): Path = usersDir.resolve(s"$id.json")

  def load(id: String): User Or ErrorMessage = load(path(id))

  private def load(p: Path): User Or ErrorMessage = Json.load[User](p).badMap(e => e.getMessage)

  private def store(user: User): Unit = Json.store(path(user.id), user)

  var userCache = Map[String, User]()

  def userUpdate(user: User): User = {
    userCache += user.id -> user
    store(user)
    user
  }
}
