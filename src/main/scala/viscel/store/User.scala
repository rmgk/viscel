package viscel.store

import viscel.shared.{Bookmark, Vid}

import scala.collection.immutable.Map

final case class User(id: String, password: String, admin: Boolean, bookmarks: Map[Vid, Bookmark]) {
  def setBookmark(collection: Vid, bookmark: Bookmark): User =
    copy(bookmarks = bookmarks.updated(collection, bookmark))
  def removeBookmark(collection: Vid): User = copy(bookmarks = bookmarks - collection)
}

final case class LegacyUser(id: String, password: String, admin: Boolean, bookmarks: Map[Vid, Int]) {
  def toUser: User = User(id, password, admin, bookmarks.mapValues(Bookmark(_, 0)))
}


