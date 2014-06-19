package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.{Label, Node}
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._

import scala.collection.JavaConversions._


trait ViscelNode extends StrictLogging {
	def self: Node
	def selfLabel: Label
	def nid = Neo.txs { self.getId }
	def created = Neo.txs { self[Long]("created") }

	if (selfLabel !== label.Unlabeled) require(Neo.txs { self.getLabels.exists(_ === selfLabel) }, s"node label did not match $selfLabel")

	def deleteNode(warn: Boolean = true): Unit = {
		if (warn) logger.warn(s"deleting node $this")
		Neo.delete(self)
	}

	override def equals(other: Any) = other match {
		case o: ViscelNode => self === o.self
		case _ => false
	}

	override def hashCode: Int = self.hashCode

	override def toString: String = s"${ selfLabel.name }($nid)"
}

case class UnlabeledNode(self: Node) extends ViscelNode {
	def selfLabel = label.Unlabeled
}

case class BookmarkNode(self: Node) extends ViscelNode {
	def selfLabel = label.Bookmark
}

object ViscelNode {
	def apply(node: Node): ViscelNode Or ErrorMessage = Neo.txs { node.getLabels.to[List] } match {
		case List() => Good(UnlabeledNode(node))
		case List(l) if l === label.Chapter => Good(ChapterNode(node))
		case List(l) if l === label.Collection => Good(CollectionNode(node))
		case List(l) if l === label.Config => Good(ConfigNode())
		case List(l) if l === label.Asset => Good(AssetNode(node))
		case List(l) if l === label.User => Good(UserNode(node))
		case List(l) if l === label.Bookmark => Good(BookmarkNode(node))
		case List(l) if l === label.Page => Good(PageNode(node))
		case List(l) if l === label.Blob => Good(BlobNode(node))
		case List(l) if l === label.Core => Good(CoreNode(node))
		case List(l) => Bad(s"unhandled label $l for $node")
		case list: List[_] => Bad(s"to many labels $list for $node")
	}
	def apply(id: Long): ViscelNode Or ErrorMessage = attempt { Neo.tx { _.getNodeById(id) } }.fold(apply, t => Bad(t.toString))
}
