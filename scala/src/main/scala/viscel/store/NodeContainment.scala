package viscel.store

import org.neo4j.graphdb.Node
import scala.collection.JavaConversions._
import viscel._
import org.scalactic.TypeCheckedTripleEquals._

trait NodeContainer[ChildType <: ContainableNode[ChildType]] extends ViscelNode {

	def makeChild: Node => ChildType

	def last: Option[ChildType] = Neo.txs { self.to(rel.last).map { makeChild } }
	def first: Option[ChildType] = Neo.txs { self.to(rel.first).map { makeChild } }
	def size: Int = Neo.txs { last.map { _.position }.getOrElse(0) }

	def apply(pos: Int) = Neo.txts(s"query $this($pos)") {
		children.find { _.position === pos }
	}

	def children = Neo.txs {
		self.incoming(rel.parent).map { _.getStartNode.pipe { makeChild } }.toIndexedSeq
	}

	def append(childNode: ChildType) = Neo.tx { db =>
		val node = childNode.self
		val lastPos = last match {
			case Some(en) =>
				en.self.incoming(rel.last).foreach { _.delete }
				en.self.createRelationshipTo(node, rel.next)
				en.position
			case None =>
				self.createRelationshipTo(node, rel.first)
				0
		}
		self.createRelationshipTo(node, rel.last)
		node.createRelationshipTo(self, rel.parent)
		node.setProperty("position", lastPos + 1)
		childNode
	}

}

trait ContainableNode[SelfType] extends ViscelNode {
	def makeSelf: Node => SelfType
	def next: Option[SelfType] = Neo.txs { self.to(rel.next).map { makeSelf } }
	def prev: Option[SelfType] = Neo.txs { self.from(rel.next).map { makeSelf } }
	def position: Int = Neo.txs { self[Int]("position") }
}
