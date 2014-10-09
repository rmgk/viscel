package viscel.store

import org.neo4j.graphdb.Node
import viscel.description._
import viscel.store.label.SimpleLabel
import viscel.store.nodes._

import scala.annotation.tailrec
import scala.collection.JavaConverters._



abstract class ArchiveNode extends ViscelNode {


	def description: Description

	@tailrec
	private def layerAcc(acc: List[ArchiveNode]): List[ArchiveNode] = narc match {
		case None => this :: acc
		case Some(next) => next.layerAcc(this :: acc)
	}
	def layer: List[ArchiveNode] = Neo.txs { layerAcc(Nil).reverse }

	def narc: Option[ArchiveNode] = self.to(rel.narc).map { ArchiveNode(_) }
	def narc_=(en: ArchiveNode) = self.to_=(rel.narc, en.self)
	def parc: Option[ArchiveNode] = self.from(rel.narc).map { ArchiveNode(_) }
	def parc_=(en: ArchiveNode) = self.from_=(rel.narc, en.self)

	def prev: Option[ArchiveNode] = Neo.txs { ArchiveManipulation.prev(self).map(ArchiveNode(_)) }

	def next: Option[ArchiveNode] = Neo.txs { ArchiveManipulation.next(self).map(ArchiveNode(_)) }

	def collection: CollectionNode = Neo.txs {
		@tailrec
		def rewind(node: Node): Node = {
			val begin = ArchiveManipulation.layerBegin(node)
			begin.from(rel.describes) match {
				case None => begin
				case Some(upper) => rewind(upper)
			}
		}
		CollectionNode(rewind(self))
	}

	def setChecked() = Neo.txs { self.setProperty("checked", System.currentTimeMillis()) }
	def lastCheck(difference: scala.concurrent.duration.Duration) = Neo.txs {
		val checked = self.get[Long]("checked").getOrElse(0L)
		val now = System.currentTimeMillis()
		now - checked > difference.toMillis
	}

	@tailrec
	final def findBackward[R](p: PartialFunction[ArchiveNode, R]): Option[R] = {
		if (p.isDefinedAt(this)) Some(p(this))
		else prev match {
			case None => None
			case Some(prevNode) => prevNode.findBackward(p)
		}
	}

	@tailrec
	final def findForward[R](p: PartialFunction[ArchiveNode, R]): Option[R] = {
		if (p.isDefinedAt(this)) Some(p(this))
		else next match {
			case None => None
			case Some(nextNode) => nextNode.findForward(p)
		}
	}
	def deleteRecursive()(implicit neo: Neo): Unit = Neo.delete(self)
}

object ArchiveNode {

	def apply(node: Node): ArchiveNode = Neo.txs { node.getLabels.asScala.toList } match {
		case List(l) => SimpleLabel(l) match {
			case label.Chapter => ChapterNode(node)
			case label.Asset => AssetNode(node)
			case label.Page => PageNode(node)
			case label.Core => CoreNode(node)
		}
		case List(l) => throw new IllegalArgumentException(s"unhandled label $l for $node")
		case list: List[_] => throw new IllegalArgumentException(s"to many labels $list for $node")
	}

	def apply(id: Long): ArchiveNode = apply(Neo.tx { _.getNodeById(id) })

}
