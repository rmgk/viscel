package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import util.Try
import viscel._
import scala.collection.JavaConversions._

trait ViscelNode extends StrictLogging {
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
	def apply(node: Node): Try[ViscelNode] = Neo.txs { node.getLabels.to[List] } match {
		case List() => Try(new UnlabeledNode(node))
		case List(l) if l == label.Chapter => Try(ChapterNode(node))
		case List(l) if l == label.Collection => Try(CollectionNode(node))
		case List(l) if l == label.Config => Try(ConfigNode())
		case List(l) if l == label.Element => Try(ElementNode(node))
		case List(l) if l == label.User => Try(UserNode(node))
		case List(l) if l == label.Bookmark => Try(new ViscelNode { def self = node; def selfLabel = label.Bookmark })
		case List(l) if l == label.Structure => Try(StructureNode(node))
		case List(l) if l == label.Page => Try(PageNode(node))
		case List(l) => failure(s"unhandled label $l for $node")
		case list: List[_] => failure(s"to many labels $list for $node")
	}
	def apply(id: Long): Try[ViscelNode] = Try { Neo.tx { _.getNodeById(id) } }.flatMap { apply }
}
