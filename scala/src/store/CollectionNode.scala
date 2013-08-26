package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import util.Try
import viscel.time

class CollectionNode(val self: Node) {
	require(Neo.txs { self.getLabels.exists(_ == label.Collection) })

	def nid = Neo.txs { self.getId }
	def id = Neo.txs { self[String]("id") }
	def name = Neo.txs { self.get[String]("name").getOrElse(id) }
	def last = Neo.txs { self.to("last").map { ElementNode(_) } }
	def first = Neo.txs { self.to("first").map { ElementNode(_) } }
	def size = Neo.txs { last.map { _.self[Int]("position") }.getOrElse(0) }

	def apply(pos: Int) = Neo.txs {
		self.getRelationships(Direction.INCOMING, rel.parent).map { r => ElementNode { r.getStartNode } }.find { _.position == pos }
	}

	def children = Neo.txs {
		self.getRelationships(Direction.INCOMING, rel.parent).map { r => ElementNode { r.getStartNode } }.toIndexedSeq
	}

	override def equals(other: Any) = other match {
		case o: CollectionNode => self == o.self
		case _ => false
	}

	override def toString = s"Collection($id)"

	// def add(element: Element, pred: Option[ElementNode] = None) = Neo.txs { append(ElementNode.create(element), pred) }

	def append(elementNode: ElementNode, pred: Option[ElementNode] = None) = Neo.tx { db =>
		val node = elementNode.self
		val lastPos = pred.orElse(last) match {
			case Some(en) =>
				en.self.getSingleRelationship(rel.last, Direction.INCOMING).delete
				en.self.createRelationshipTo(node, rel.next)
				en.position
			case None =>
				self.createRelationshipTo(node, rel.first)
				0
		}
		self.createRelationshipTo(node, rel.last)
		node.createRelationshipTo(self, rel.parent)
		node.setProperty("position", lastPos + 1)
		elementNode
	}

}

object CollectionNode {
	def apply(node: Node) = new CollectionNode(node)
	def apply(id: String) = Neo.node(label.Collection, "id", id).map { new CollectionNode(_) }

	def create(id: String, name: Option[String] = None) = CollectionNode(Neo.create(label.Collection, (Seq("id" -> id) ++ name.map { "name" -> _ }): _*))
}
