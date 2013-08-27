package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.RelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import util.Try
import viscel.time
import viscel._

trait NodeContainer[ChildType <: ContainableNode[ChildType]] {

	def self: Node
	def containRelation: RelationshipType
	def makeChild: Node => ChildType

	def last = Neo.txs { self.to(rel.last).map { makeChild } }
	def first = Neo.txs { self.to(rel.first).map { makeChild } }
	def size = Neo.txs { last.map { _.position }.getOrElse(0) }

	def apply(pos: Int) = Neo.txs {
		children.find { _.position == pos }
	}

	def children = Neo.txs {
		self.incoming(containRelation).map { _.getStartNode.pipe { makeChild } }.toIndexedSeq
	}

	def append(childNode: ChildType) = Neo.tx { db =>
		val node = childNode.self
		val lastPos = last match {
			case Some(en) =>
				en.self.getSingleRelationship(rel.last, Direction.INCOMING).delete
				en.self.createRelationshipTo(node, rel.next)
				en.position
			case None =>
				self.createRelationshipTo(node, rel.first)
				0
		}
		self.createRelationshipTo(node, rel.last)
		node.createRelationshipTo(self, containRelation)
		node.setProperty("position", lastPos + 1)
		childNode
	}

	def drop() = Neo.txs {
		for {
			lnode <- last
			pnode <- lnode.prev
		} {
			pnode.self.createRelationshipTo(self, containRelation)
			lnode.delete
		}
	}

}

trait ContainableNode[SelfType] {

	def self: Node
	def makeSelf: Node => SelfType

	def next: Option[SelfType] = Neo.txs { self.to(rel.next).map { makeSelf } }
	def prev: Option[SelfType] = Neo.txs { self.from(rel.next).map { makeSelf } }

	def position: Int = Neo.txs { self[Int]("position") }

	def delete(): Unit

}