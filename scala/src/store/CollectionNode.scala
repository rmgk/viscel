package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import viscel.Element
import util.Try
import viscel.time

class CollectionNode(val self: Node) {
	def id = Neo.txs { self[String]("id") }

	def last = Neo.txs { self.to("last").map { ElementNode(_) } }
	def first = Neo.txs { self.to("first").map { ElementNode(_) } }
	def bookmark = Neo.txs { self.to("bookmark").map { ElementNode(_) } }

	def size = Neo.txs { last.map { _.self[Int]("position") }.getOrElse(0) }

	def apply(pos: Int) = Neo.execute("""
		|start self = node({self})
		|match (self) <-[:parent]- (node)
		|where node.position = {pos}
		|return node limit 1
		""",
		"self" -> self,
		"pos" -> pos).columnAs[Node]("node").toList.headOption.map { ElementNode(_) }

	def bookmark(pos: Int): Option[ElementNode] = apply(pos).map(bookmark(_))
	def bookmark(en: ElementNode): ElementNode = Neo.txs {
		Option(self.getSingleRelationship("bookmark", Direction.OUTGOING)).foreach { _.delete }
		self.createRelationshipTo(en.self, "bookmark")
		en
	}

	def bookmarkDelete() = Neo.txs { Option(self.getSingleRelationship("bookmark", Direction.OUTGOING)).foreach { _.delete } }

	def unread = Neo.txs { for (bm <- bookmark; l <- last) yield l.position - bm.position }

	override def equals(other: Any) = other match {
		case o: CollectionNode => self == o.self
		case _ => false
	}

	override def toString = id

	def add(element: Element, pred: Option[ElementNode] = None) = Neo.txs { append(ElementNode.create(element), pred) }

	def append(elementNode: ElementNode, pred: Option[ElementNode] = None) = Neo.tx { db =>
		val node = elementNode.self
		val lastPos = pred.orElse(last) match {
			case Some(en) =>
				en.self.getSingleRelationship("last", Direction.INCOMING).delete
				en.self.createRelationshipTo(node, "next")
				en.position
			case None =>
				self.createRelationshipTo(node, "first")
				0
		}
		self.createRelationshipTo(node, "last")
		node.createRelationshipTo(self, "parent")
		node.setProperty("position", lastPos + 1)
		elementNode
	}

}

object CollectionNode {
	def apply(node: Node) = new CollectionNode(node)
	def apply(id: String) = Neo.node(labelCollection, "id", id).map { new CollectionNode(_) }

	def create(id: String, name: Option[String] = None) = CollectionNode(Neo.create(labelCollection, (Seq("id" -> id) ++ name.map { "name" -> _ }): _*))

	def list = Neo.execute("""
		|match (col: Collection)
		|return col
		""").columnAs[Node]("col").map { CollectionNode(_) }.toList
}
