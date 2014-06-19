package viscel.store

import org.neo4j.graphdb.Node
import org.scalactic._
import viscel.description._
import viscel.store._


import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.{Label, Node}
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable


trait ArchiveNode extends ViscelNode {

	import viscel.store.ArchiveManipulation._

	def description: Description

	@tailrec
	private def layerAcc(acc: List[ArchiveNode]): List[ArchiveNode] = narc match {
		case None => this :: acc
		case Some(next) => next.layerAcc(this :: acc)
	}
	def layer: List[ArchiveNode] = Neo.txs { layerAcc(Nil).reverse }

	def narc: Option[ArchiveNode] = self.to(rel.narc).map { ArchiveNode(_) }
	def narc_=(en: ArchiveNode) = self.to_=(rel.narc, en.self)
	def parc: Option[ArchiveNode] =self.from(rel.narc).map { ArchiveNode(_) }
	def parc_=(en: ArchiveNode) = self.from_=(rel.narc, en.self)

	def prev: Option[ArchiveNode] = Neo.txs { ArchiveManipulation.prev(self).map(ArchiveNode(_))	}

	def next: Option[ArchiveNode] = Neo.txs { ArchiveManipulation.next(self).map(ArchiveNode(_)) }

	def collection: CollectionNode = Neo.txs {
		@tailrec
		def rewind(node: Node): Node = {
			val begin = layerBegin(node)
			begin.from(rel.describes) match {
				case None => begin
				case Some(upper) => rewind(upper)
			}
		}
		CollectionNode(rewind(self))
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
	def deleteRecursive(): Unit = deleteNode(warn = false)
}

object ArchiveNode {

	def apply(node: Node): ArchiveNode = Neo.txs { node.getLabels.asScala.toList } match {
		case List(l) if l === label.Chapter => ChapterNode(node)
		case List(l) if l === label.Asset => AssetNode(node)
		case List(l) if l === label.Page => PageNode(node)
		case List(l) if l === label.Core => CoreNode(node)
		case List(l) => throw new IllegalArgumentException(s"unhandled label $l for $node")
		case list: List[_] => throw new IllegalArgumentException(s"to many labels $list for $node")
	}

	def apply(id: Long): ArchiveNode = apply(Neo.tx { _.getNodeById(id) })

}
