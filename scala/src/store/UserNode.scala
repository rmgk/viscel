package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import viscel.Element
import util.Try

class UserNode(val self: Node) {
	def id = Neo.txs { self.getId }
	def name = Neo.txs { self[String]("name") }
	def password = Neo.txs { self[String]("password") }

	def getBookmark(cn: CollectionNode) = Neo.txs { bookmarks.find(_.collection == cn) }

	//def bookmark(pos: Int): Option[ElementNode] = apply(pos).map(bookmark(_))
	def setBookmark(en: ElementNode) = Neo.txs {
		Neo.execute("""
			|start user = node({self}), en = node({en})
			|match (user) -[bm :bookmark]-> () -[:parent]-> (col) <-[:parent]- (en)
			|delete bm
			|""",
			"self" -> self,
			"en" -> en.self)
		self.createRelationshipTo(en.self, "bookmark")
	}

	def bookmarks = Neo.txs {
		self.getRelationships(Direction.OUTGOING, "bookmark").map { r => ElementNode { r.getEndNode } }
	}

	def deleteBookmark(cn: CollectionNode) = Neo.txs {
		for {
			en <- getBookmark(cn)
			rel <- Option(self.getSingleRelationship("bookmark", Direction.INCOMING))
		} rel.delete
	}

	//def unread = Neo.txs { for (bm <- bookmark; l <- last) yield l.position - bm.position }

}

object UserNode {
	def apply(node: Node) = new UserNode(node)
	def apply(name: String) = Neo.node(labelUser, "name", name).map { new UserNode(_) }

	def create(name: String, password: String) = UserNode(Neo.create(labelUser, "name" -> name, "password" -> password))
}
