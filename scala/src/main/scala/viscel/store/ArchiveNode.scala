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
import scala.collection.JavaConversions._
import scala.collection.mutable


trait ArchiveNode extends ViscelNode {

	import viscel.store.ArchiveNode._

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

	def prev: Option[ArchiveNode] = Neo.txs { ArchiveNode.prev(self).map(ArchiveNode(_))	}

	def next: Option[ArchiveNode] = Neo.txs { ArchiveNode.next(self).map(ArchiveNode(_)) }

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

	def apply(node: Node): ArchiveNode = Neo.txs { node.getLabels.to[List] } match {
		case List(l) if l === label.Chapter => ChapterNode(node)
		case List(l) if l === label.Asset => AssetNode(node)
		case List(l) if l === label.Page => PageNode(node)
		case List(l) if l === label.Core => CoreNode(node)
		case List(l) => throw new IllegalArgumentException(s"unhandled label $l for $node")
		case list: List[_] => throw new IllegalArgumentException(s"to many labels $list for $node")
	}

	def apply(id: Long): ArchiveNode =  apply(Neo.tx { _.getNodeById(id) })

	@tailrec
	private[ArchiveNode] def layerBegin(node: Node): Node = node.from(rel.narc) match {
		case None => node
		case Some(prev) => layerBegin(prev)
	}

	@tailrec
	private[ArchiveNode] def layerEnd(node: Node): Node = node.to(rel.narc) match {
		case None => node
		case Some(next) => layerEnd(next)
	}

	@tailrec
	private[ArchiveNode] def uppernext(node: Node): Option[Node] = {
		layerBegin(node).from(rel.describes) match {
			case None => None
			case Some(upper) => upper.to(rel.narc) match {
				case None => uppernext(upper)
				case result@Some(_) => result
			}
		}
	}

	@tailrec
	private[ArchiveNode] def rightmost(node: Node): Node = {
		val end = layerEnd(node)
		end.to(rel.describes) match {
			case None => end
			case Some(lower) => rightmost(lower)
		}
	}

	private[ArchiveNode] def prev(node: Node): Option[Node] = {
		node.from(rel.narc) match {
			case None =>
				node.from(rel.describes).flatMap { upper =>
					if (upper.hasLabel(label.Collection)) None
					else Some(upper)
				}
			case somePrev@Some(prev) =>
				prev.to(rel.describes) match {
					case None => somePrev
					case Some(lower) => Some(rightmost(lower))
				}
		}
	}

	private[ArchiveNode] def next(node: Node): Option[Node] = {
		if (node.hasLabel(label.Page)) node.to(rel.describes).orElse(node.to(rel.narc)).orElse(uppernext(node))
		else node.to(rel.narc).orElse(uppernext(node))
	}

	def connectLayer(layer: List[ArchiveNode]) = Neo.txs {
		layer.reduceLeftOption{(prev, next) => prev.narc = next ; next }
		layer.lastOption.foreach(_.self.outgoing(rel.narc).foreach(_.delete()))
		layer.headOption.foreach(_.self.incoming(rel.narc).foreach(_.delete()))
		layer
	}


	def create(desc: Description): ArchiveNode = {
		desc match {
			case FailedDescription(reason) => throw new IllegalArgumentException(reason.toString())
			case Pointer(loc, pagetype) => PageNode.create(loc, pagetype)
			case Chapter(name, props) => ChapterNode.create(name, props)
			case Asset(source, origin, props) => AssetNode.create(source = source, origin = origin, attributes = props)
			case CoreDescription(kind, id, name, props) => CoreNode.updateOrCreate(kind, id, name, props)
		}
	}

	def replaceLayer(oldLayer: List[ArchiveNode], oldDescriptions: List[Description], descriptions: List[Description]): List[ArchiveNode] = {
		val oldMap = mutable.Map(oldDescriptions zip oldLayer: _*)
		val newLayer = descriptions.map { desc =>
			oldMap.get(desc) match {
				case None => create(desc)
				case Some(oldNode) =>
					oldMap.remove(desc)
					oldNode
			}
		}
		oldMap.mapValues(_.deleteRecursive())
		connectLayer(newLayer)
	}

	def applyDescription(target: DescribingNode, descriptions: List[Description]): List[ArchiveNode] = Neo.txs {
		val oldLayer = target.describes.toList.flatMap(_.layer)
		val oldDescriptions = oldLayer.map(_.description)
		if (oldDescriptions === descriptions) oldLayer
		else {
			val newLayer = replaceLayer(oldLayer, oldDescriptions, descriptions)
			target.describes = newLayer.headOption
			newLayer
		}
	}
}
