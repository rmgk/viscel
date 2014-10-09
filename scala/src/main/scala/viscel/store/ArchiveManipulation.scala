package viscel.store

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.description.Story

import scala.annotation.tailrec
import scala.collection.mutable

object ArchiveManipulation {

	def fixSkiplist(asset: coin.Asset): Unit = {
		val nextAsset_? = asset.next.flatMap(_.findForward { case asset: coin.Asset => asset })
		nextAsset_? match {
			case None => asset.self.outgoing(rel.skip).foreach(_.delete())
			case Some(nextAsset) => asset.self.to_=(rel.skip, nextAsset.self)
		}
		val prevAsset_? = asset.prev.flatMap(_.findBackward { case asset: coin.Asset => asset })
		prevAsset_? match {
			case None => asset.self.incoming(rel.skip).foreach(_.delete())
			case Some(prevAsset) => prevAsset.self.to_=(rel.skip, asset.self)
		}
	}

	def connectLayer(layer: List[Node]) = {
		layer.reduceLeftOption { (prev, next) => prev.to_=(rel.narc, next); next }
		layer.lastOption.foreach(_.outgoing(rel.narc).foreach(_.delete()))
		layer.headOption.foreach(_.incoming(rel.narc).foreach(_.delete()))
		layer
	}

	@tailrec
	def deleteRecursive(nodes: List[Node])(implicit neo: Neo): Unit = nodes match {
		case Nil => ()
		case list =>
			val below = list.flatMap(_.to(rel.describes)).flatMap { Traversal.layer }
			list.foreach(Neo.delete)
			deleteRecursive(below)
	}

	def replaceLayer(oldLayer: List[Node], oldNarration: List[Story], newNarration: List[Story])(implicit neo: Neo): List[Node] = {
		val oldMap: mutable.Map[Story, Node] = mutable.Map(oldNarration zip oldLayer: _*)
		val newLayer: List[Node] = newNarration.map { story =>
			oldMap.get(story) match {
				case None => Vault.create.fromStory(story).self
				case Some(oldCoin) =>
					oldMap.remove(story)
					oldCoin
			}
		}
		deleteRecursive(oldMap.values.toList)
		connectLayer(newLayer)
	}

	def applyNarration(target: Node, narration: List[Story])(implicit neo: Neo): List[Node] = neo.txs {
		val oldLayer = target.to(rel.describes).toList.flatMap(Traversal.layer)
		val oldNarration = oldLayer.map(ArchiveNode(_).story)
		if (oldNarration === narration) oldLayer
		else {
			val newLayer = replaceLayer(oldLayer, oldNarration, narration)
			newLayer.headOption.foreach{ head => target.to_=(rel.describes, head) }
			newLayer.map(ArchiveNode.apply).collect { case asset @ coin.Asset(_) => fixSkiplist(asset) }
			newLayer
		}
	}
}
