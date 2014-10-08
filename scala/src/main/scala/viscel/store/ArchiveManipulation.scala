package viscel.store

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.description._

import scala.annotation.tailrec
import scala.collection.mutable

object ArchiveManipulation {

	@tailrec
	def layerBegin(node: Node): Node = node.from(rel.narc) match {
		case None => node
		case Some(prev) => layerBegin(prev)
	}

	@tailrec
	def layerEnd(node: Node): Node = node.to(rel.narc) match {
		case None => node
		case Some(next) => layerEnd(next)
	}

	@tailrec
	def uppernext(node: Node): Option[Node] = {
		layerBegin(node).from(rel.describes) match {
			case None => None
			case Some(upper) => upper.to(rel.narc) match {
				case None => uppernext(upper)
				case result@Some(_) => result
			}
		}
	}

	@tailrec
	def rightmost(node: Node): Node = {
		val end = layerEnd(node)
		end.to(rel.describes) match {
			case None => end
			case Some(lower) => rightmost(lower)
		}
	}

	def prev(node: Node): Option[Node] = {
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

	def next(node: Node): Option[Node] = {
		if (node.hasLabel(label.Page)) node.to(rel.describes).orElse(node.to(rel.narc)).orElse(uppernext(node))
		else node.to(rel.narc).orElse(uppernext(node))
	}

	def fixSkiplist(asset: AssetNode): Unit = {
		val nextAsset_? = asset.next.flatMap(_.findForward { case asset: AssetNode => asset })
		nextAsset_? match {
			case None => asset.self.outgoing(rel.skip).foreach(_.delete())
			case Some(nextAsset) => asset.self.to_=(rel.skip, nextAsset.self)
		}
		val prevAsset_? = asset.prev.flatMap(_.findBackward { case asset: AssetNode => asset })
		prevAsset_? match {
			case None => asset.self.incoming(rel.skip).foreach(_.delete())
			case Some(prevAsset) => prevAsset.self.to_=(rel.skip, asset.self)
		}
	}

	def connectLayer(layer: List[ArchiveNode]) = {
		layer.reduceLeftOption { (prev, next) => prev.narc = next; next }
		layer.lastOption.foreach(_.self.outgoing(rel.narc).foreach(_.delete()))
		layer.headOption.foreach(_.self.incoming(rel.narc).foreach(_.delete()))
		layer
	}

	def create(desc: Description): ArchiveNode = {
		desc match {
			case FailedDescription(reason) => throw new IllegalArgumentException(reason.toString())
			case Pointer(loc, pagetype) => PageNode.create(loc, pagetype)
			case chap@Chapter(_, _) => ChapterNode.create(chap)
			case asset@Asset(_, _, _) => AssetNode.create(asset)
			case core@CoreDescription(_, _, _, _) => CoreNode.updateOrCreate(core)
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

	def applyDescription(target: DescribingNode, descriptions: List[Description])(implicit neo: Neo): List[ArchiveNode] = neo.txs {
		val oldLayer = target.describes.toList.flatMap(_.layer)
		val oldDescriptions = oldLayer.map(_.description)
		if (oldDescriptions === descriptions) oldLayer
		else {
			val newLayer = replaceLayer(oldLayer, oldDescriptions, descriptions)
			target.describes = newLayer.headOption
			newLayer.collect { case asset: AssetNode => fixSkiplist(asset) }
			newLayer
		}
	}
}
