package viscel.store

import scala.collection.immutable.Map

final case class User(id: String, password: String, admin: Boolean, bookmarks: Map[String, Int]) {
	def setBookmark(collection: String, position: Int): User = copy(bookmarks = bookmarks.updated(collection, position))
	def removeBookmark(collection: String): User = copy(bookmarks = bookmarks - collection)
}


