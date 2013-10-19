package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import util.Try
import viscel.time
import viscel._

trait ViscelNode extends Logging {
	def self: Node
	def selfLabel: Label
	def nid = Neo.txs { self.getId }
	def created = Neo.txs { self[Long]("created") }

	if (selfLabel != label.Unlabeled) require(Neo.txs { self.getLabels.exists(_ == selfLabel) }, s"node label did not match $selfLabel")

	def deleteNode(warn: Boolean = true): Unit = {
		if (warn) logger.warn(s"deleting node $this")
		Neo.delete(self)
	}

	override def equals(other: Any) = other match {
		case o: ViscelNode => self == o.self
		case _ => false
	}

	override def hashCode: Int = self.hashCode

	override def toString: String = s"${selfLabel.name}($nid)"
}

class UnlabeledNode(val self: Node) extends ViscelNode {
	def selfLabel = label.Unlabeled
}

object ViscelNode {
	def apply(node: Node): Try[ViscelNode] = Neo.txs { node.getLabels.toList } match {
		case List() => Try(new UnlabeledNode(node))
		case List(l) if l == label.Chapter => Try(ChapterNode(node))
		case List(l) if l == label.Collection => Try(CollectionNode(node))
		case List(l) if l == label.Config => Try(ConfigNode())
		case List(l) if l == label.Element => Try(ElementNode(node))
		case List(l) if l == label.User => Try(UserNode(node))
		case List(l) if l == label.Bookmark => Try(new ViscelNode { def self = node; def selfLabel = label.Bookmark })
		case List(l) => failure(s"unhandled label $l")
		case list @ List(_) => failure(s"to many labels $list")
	}
	def apply(id: Long): Try[ViscelNode] = Try { Neo.tx { _.getNodeById(id) } }.flatMap { apply }
}
