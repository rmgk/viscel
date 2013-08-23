package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import util.Try
import viscel.Element
import viscel.time

/**
 * A user node currently encodes the bookmarks of the user.
 *
 * If a user `u` has a bookmark `b` to the element `e` of collection `c`
 * the graph will look somwhat like this
 *
 * (u) -[:bookmarked]-> (b)
 * (c) -[:bookmark]-> (b) -[:bookmarks]-> (e)
 */
class UserNode(val self: Node) extends Logging {
	def nid = Neo.txs { self.getId }
	def name = Neo.txs { self[String]("name") }
	def password = Neo.txs { self[String]("password") }

	def getBookmark(cn: CollectionNode) = Neo.txts("get bookmark") { getBookmarkNode(cn).flatMap { _.to("bookmarks") }.map { ElementNode(_) } }

	override def equals(other: Any) = other match {
		case o: UserNode => self == o.self
		case _ => false
	}

	override def toString = s"User($name)"

	//def bookmark(pos: Int): Option[ElementNode] = apply(pos).map(bookmark(_))
	def setBookmark(en: ElementNode) = Neo.txt(s"create bookmark ${en.collection.id}:${en.position} for ${name}") { db =>
		val bmn = getBookmarkNode(en.collection).map { bmn =>
			bmn.outgoing("bookmarks").foreach { _.delete }
			bmn
		}.getOrElse {
			val bmn = db.createNode(labelBookmark)
			self.createRelationshipTo(bmn, "bookmarked")
			en.collection.self.createRelationshipTo(bmn, "bookmark")
			bmn
		}
		bmn.createRelationshipTo(en.self, "bookmarks")
	}

	def bookmarks = Neo.txs {
		self.outgoing("bookmarked").map { _.getEndNode }.flatMap { _.to("bookmarks") }.map { ElementNode(_) }
	}

	def deleteBookmark(cn: CollectionNode) = Neo.txts(s"delete bookmark ${cn.id} for ${name}") {
		getBookmarkNode(cn).foreach { bmn =>
			bmn.getRelationships().foreach { _.delete }
			bmn.delete
		}
	}

	def getBookmarkNode(cn: CollectionNode) = Neo.txs {
		cn.self.outgoing("bookmark").map { _.getEndNode }.find { bmn => bmn.from("bookmarked").get == this.self }
	}

	//def unread = Neo.txs { for (bm <- bookmark; l <- last) yield l.position - bm.position }

}

object UserNode {
	def apply(node: Node) = new UserNode(node)
	def apply(name: String) = Neo.node(labelUser, "name", name).map { new UserNode(_) }

	def create(name: String, password: String) = UserNode(Neo.create(labelUser, "name" -> name, "password" -> password))
}
