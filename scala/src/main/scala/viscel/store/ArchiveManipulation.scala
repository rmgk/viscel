package viscel.store

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.description.Story
import viscel.store.Traversal.{findBackward, findForward}

import scala.annotation.tailrec
import scala.collection.mutable

object ArchiveManipulation {

	private def fixSkiplist(currentAsset: Node): Unit = {
		val nextAsset_? = Traversal.next(currentAsset).flatMap(findForward(Coin.isAsset))
		nextAsset_? match {
			case None => currentAsset.outgoing(rel.skip).foreach(_.delete())
			case Some(nextAsset) => currentAsset.to_=(rel.skip, nextAsset.self)
		}
		val prevAsset_? = Traversal.prev(currentAsset).flatMap(findBackward(Coin.isAsset))
		prevAsset_? match {
			case None => currentAsset.incoming(rel.skip).foreach(_.delete())
			case Some(prevAsset) => currentAsset.from_=(rel.skip, prevAsset.self)
		}
	}

	private def connectLayer(layer: List[Node]) = {
		layer.reduceLeftOption { (prev, next) => prev.to_=(rel.narc, next); next }
		layer.lastOption.foreach(_.outgoing(rel.narc).foreach(_.delete()))
		layer.headOption.foreach(_.incoming(rel.narc).foreach(_.delete()))
		layer
	}

	@tailrec
	private def deleteRecursive(nodes: List[Node])(implicit neo: Neo): Unit = nodes match {
		case Nil => ()
		case list =>
			val below = list.flatMap(_.to(rel.describes)).flatMap { Traversal.layer }
			list.foreach(neo.delete)
			deleteRecursive(below)
	}

	private def replaceLayer(oldLayer: List[Node], oldNarration: List[Story], newNarration: List[Story])(implicit neo: Neo): List[Node] = {
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
		val oldLayer = Traversal.layerBelow(target)
		val oldNarration = oldLayer.map(Coin.hasStory).flatten.map(_.story)
		if (oldNarration === narration) oldLayer
		else {
			val newLayer = replaceLayer(oldLayer, oldNarration, narration)
			newLayer.headOption.foreach { head => target.to_=(rel.describes, head) }
			newLayer.filter { _.hasLabel(label.Asset) }.foreach(fixSkiplist)
			newLayer
		}
	}
}
