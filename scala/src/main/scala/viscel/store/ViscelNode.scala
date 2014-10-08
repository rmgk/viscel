package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.{Label, Node}
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._

import scala.collection.JavaConverters._


trait ViscelNode extends StrictLogging {
  def self: Node
  def selfLabel: Label
  def nid = Neo.txs { self.getId }
  def created = Neo.txs { self[Long]("created") }

  if (selfLabel !== label.Unlabeled) Predef.require(Neo.txs { self.getLabels.asScala.exists(_ === selfLabel) }, s"node label did not match $selfLabel")

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
  def apply(node: Node): ViscelNode Or ErrorMessage = Neo.txs { node.getLabels.asScala.to[List] } match {
    case List() => Good(UnlabeledNode(node))
    case List(l) => l match {
      case label.Chapter => Good(ChapterNode(node))
      case label.Collection => Good(CollectionNode(node))
      case label.Config => Good(ConfigNode())
      case label.Asset => Good(AssetNode(node))
      case label.User => Good(UserNode(node))
      case label.Bookmark => Good(BookmarkNode(node))
      case label.Page => Good(PageNode(node))
      case label.Blob => Good(BlobNode(node))
      case label.Core => Good(CoreNode(node))
    }
    case List(l) => Bad(s"unhandled label $l for $node")
    case list: List[_] => Bad(s"to many labels $list for $node")
  }
  def apply(id: Long): ViscelNode Or ErrorMessage = attempt { Neo.tx { _.getNodeById(id) } }.fold(apply, t => Bad(t.toString))
}
