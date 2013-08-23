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

class UserNode(val self: Node) extends Logging {
	def nid = Neo.txs { self.getId }
	def name = Neo.txs { self[String]("name") }
	def password = Neo.txs { self[String]("password") }

	def getBookmark(cn: CollectionNode) = Neo.txs { bookmarks.find(_.collection == cn) }

	//def bookmark(pos: Int): Option[ElementNode] = apply(pos).map(bookmark(_))
	def setBookmark(en: ElementNode) = Neo.txts(s"create bookmark ${en.collection.id}:${en.position} for ${name}") {
		deleteBookmark(en.collection)
		self.createRelationshipTo(en.self, "bookmark")
	}

	def bookmarks = Neo.txs {
		self.getRelationships(Direction.OUTGOING, "bookmark").map { r => ElementNode { r.getEndNode } }
	}

	def deleteBookmark(cn: CollectionNode) = Neo.txts(s"delete bookmark ${cn.id} for ${name}") {
		for {
			en <- getBookmark(cn)
			rel <- en.self.getRelationships("bookmark", Direction.INCOMING)
		} rel.delete
	}

	//def unread = Neo.txs { for (bm <- bookmark; l <- last) yield l.position - bm.position }

}

object UserNode {
	def apply(node: Node) = new UserNode(node)
	def apply(name: String) = Neo.node(labelUser, "name", name).map { new UserNode(_) }

	def create(name: String, password: String) = UserNode(Neo.create(labelUser, "name" -> name, "password" -> password))
}
