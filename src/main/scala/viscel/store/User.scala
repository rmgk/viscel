package viscel.store

import viscel.shared.Vid

import scala.collection.immutable.Map

final case class User(id: String, password: String, admin: Boolean, bookmarks: Map[Vid, Int]) {
  def setBookmark(collection: Vid, position: Int): User = copy(bookmarks = bookmarks.updated(collection, position))
  def removeBookmark(collection: Vid): User = copy(bookmarks = bookmarks - collection)
}


