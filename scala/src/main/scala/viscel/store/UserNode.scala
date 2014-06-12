package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import scala.language.implicitConversions
import scala.collection.JavaConversions._

/**
 * A user node currently encodes the bookmarks of the user.
 *
 * If a user `u` has a bookmark `b` to the element `e` of collection `c`
 * the graph will look somwhat like this
 *
 * (u) -[:bookmarked]-> (b)
 * (c) -[:bookmark]-> (b) -[:bookmarks]-> (e)
 */
class UserNode(val self: Node) extends ViscelNode with StrictLogging {
	def selfLabel = label.User

	def name = Neo.txs { self[String]("name") }
	def password = Neo.txs { self[String]("password") }

	def getBookmark(cn: CollectionNode) = Neo.txs { getBookmarkNode(cn).flatMap { bookmarkToElement } }

	//def bookmark(pos: Int): Option[ElementNode] = apply(pos).map(bookmark(_))
	def setBookmark(en: ElementNode) = Neo.txt(s"create bookmark ${en.collection.id}:${en.position} for ${name}") { db =>
		val bmn = getBookmarkNode(en.collection).map { bmn =>
			bmn.outgoing(rel.bookmarks).foreach { _.delete }
			bmn
		}.getOrElse {
			val bmn = Neo.create(label.Bookmark)
			self.createRelationshipTo(bmn, rel.bookmarked)
			en.collection.self.createRelationshipTo(bmn, rel.bookmark)
			bmn
		}
		bmn.createRelationshipTo(en.self, rel.bookmarks)
	}

	def bookmarks = Neo.txs {
		self.outgoing(rel.bookmarked).map { _.getEndNode }.flatMap { bookmarkToElement }
	}

	def bookmarkToElement(bmn: Node): Option[ElementNode] = Neo.txs {
		bmn.to(rel.bookmarks) match {
			case Some(n) => Some(ElementNode(n))
			case None => {
				None
				//				for {
				//					chapter <- bmn.get[Int]("chapter")
				//					page <- bmn.get[Int]("page")
				//					ncol <- bmn.from(rel.bookmark)
				//					col = CollectionNode(ncol)
				//					en <- col(chapter).flatMap { cn => cn(page).orElse(cn.last) }.orElse(col.last.flatMap { _.last })
				//				} yield (chapter, page, col, en)
				//			} match {
				//				case None =>
				//					logger.warn(s"disconnected bookmark has insufficient information, delete")
				//					Neo.delete(bmn)
				//					None
				//				case Some((chapter, page, col, en)) =>
				//					logger.warn(s"rewire disconnected bookmark $name -> ${col.name}($chapter, $page)")
				//					bmn.createRelationshipTo(en.self, rel.bookmarks)
				//					bmn.removeProperty("chapter")
				//					bmn.removeProperty("page")
				//					Some(en)
			}
		}
	}

	def deleteBookmark(cn: CollectionNode) = Neo.txts(s"delete bookmark ${cn.id} for ${name}") {
		getBookmarkNode(cn).foreach { bmn =>
			bmn.getRelationships().foreach { _.delete }
			bmn.delete
		}
	}

	def getBookmarkNode(cn: CollectionNode) = Neo.txs {
		cn.self.outgoing(rel.bookmark).map { _.getEndNode }.find { bmn => bmn.from(rel.bookmarked).get == this.self }
	}

	//def unread = Neo.txs { for (bm <- bookmark; l <- last) yield l.position - bm.position }

}

object UserNode {
	def apply(node: Node) = new UserNode(node)
	def apply(name: String) = Neo.node(label.User, "name", name).map { new UserNode(_) }

	def create(name: String, password: String) = UserNode(Neo.create(label.User, "name" -> name, "password" -> password))
}
