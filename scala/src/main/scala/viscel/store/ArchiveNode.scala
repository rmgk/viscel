package viscel.store

import org.neo4j.graphdb.Node
import viscel.description.Story
import viscel.store.label.SimpleLabel
import viscel.store.coin.{Core, Page, Asset, Chapter, Collection}

import scala.annotation.tailrec
import scala.collection.JavaConverters._



abstract class ArchiveNode extends Coin {


	def story: Story

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

	def prev: Option[ArchiveNode] = Neo.txs { Traversal.prev(self).map(ArchiveNode(_)) }

	def next: Option[ArchiveNode] = Neo.txs { Traversal.next(self).map(ArchiveNode(_)) }

	def collection: Collection = Neo.txs {
		@tailrec
		def rewind(node: Node): Node = {
			val begin = Traversal.layerBegin(node)
			begin.from(rel.describes) match {
				case None => begin
				case Some(upper) => rewind(upper)
			}
		}
		Collection(rewind(self))
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
			case label.Chapter => Chapter(node)
			case label.Asset => Asset(node)
			case label.Page => Page(node)
			case label.Core => Core(node)
		}
		case List(l) => throw new IllegalArgumentException(s"unhandled archive label $l for $node")
		case list @ _ :: _ => throw new IllegalArgumentException(s"to many labels $list for $node")
	}

}
