package viscel.store

import java.io.IOException
import java.nio.file.{Files, Path}

import viscel.server.ContentLoader
import viscel.shared.Log.{Store => Log}
import viscel.shared.{Bookmark, Vid}

import scala.collection.immutable.Map
import scala.jdk.CollectionConverters._

class Users(usersDir: Path, contentLoader: ContentLoader) {

  type Error[T] = Either[Throwable, T]

  def allBookmarks(): Seq[Vid] = {
    all() match {
      case Left(err) =>
        Log.error(s"could not load bookmarked collections: $err")
        Nil
      case Right(users) =>
        users.flatMap(_.bookmarks.keySet).distinct
    }
  }

  def all(): Error[List[User]] =
    try {
      if (!Files.isDirectory(usersDir)) Right(Nil)
      else
        Files.newDirectoryStream(usersDir, "*.json")
          .asScala.foldLeft(Right(Nil): Either[Throwable, List[User]]) { (el, f) =>
            el.flatMap(l => load(f).map(_ :: l))
          }.map(_.reverse)
    } catch {
      case e: IOException => Left(e)
    }

  def setBookmark(user: User, colid: Vid, bm: Bookmark): User = {
    Log.info(s"setBookmark $colid to $bm")
    userUpdate(user.setBookmark(colid, bm))
  }

  private def path(id: String): Path = usersDir.resolve(s"$id.json")

  def load(id: String): Error[User] = load(path(id))

  private def load(p: Path): Error[User] =
    JsoniterStorage.load[User](p)(JsoniterStorage.UserCodec)

  var userCache = Map[String, User]()

  def get(name: String): Option[User] = userCache.get(name)

  def getOrAddFirstUser(name: String, orElse: => User): Option[User] = {
    userCache.get(name).orElse(
      (load(name) match {
        case Right(g) => Some(g)
        case Left(e) =>
          Log.warn(s"could not open user $name: $e")
          val firstUser = all().fold(_ => false, _.isEmpty)
          if (firstUser) {
            Log.info(s"create initial user: $name")
            Some(orElse)
          } else None
      }).map(userUpdate)
    )
  }

  def userUpdate(user: User): User =
    synchronized {
      userCache += user.id -> user
      JsoniterStorage.store(path(user.id), user)(JsoniterStorage.UserCodec)
      user
    }
}
