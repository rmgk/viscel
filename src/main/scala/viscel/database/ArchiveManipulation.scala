package viscel.database

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.shared.Story
import viscel.store.Coin

import scala.annotation.tailrec
import scala.collection.mutable

object ArchiveManipulation {

	private def connectLayer(layer: List[Node])(implicit neo: Ntx): List[Node] = {
		layer.reduceLeftOption { (prev, next) => prev narc_= next; next }
		layer.lastOption.foreach(_.outgoing(rel.narc).foreach(_.delete()))
		layer.headOption.foreach(_.incoming(rel.narc).foreach(_.delete()))
		layer
	}

	@tailrec
	private def deleteRecursive(nodes: List[Node])(implicit neo: Ntx): Unit = nodes match {
		case Nil => ()
		case list =>
			val below = list.map(_.below).filter(null.ne).flatMap(_.layer)
			list.foreach(neo.delete)
			deleteRecursive(below)
	}

	private def replaceLayer(oldLayer: List[Node], oldNarration: List[Story], newNarration: List[Story])(implicit neo: Ntx): List[Node] = {
		val oldMap: mutable.Map[Story, Node] = mutable.Map(oldNarration zip oldLayer: _*)
		val newLayer: List[Node] = newNarration.map { story =>
			oldMap.get(story) match {
				case None => Coin.create(story)
				case Some(oldCoin) =>
					oldMap.remove(story)
					oldCoin
			}
		}
		deleteRecursive(oldMap.values.toList)
		connectLayer(newLayer)
	}

	def applyNarration(target: Node, narration: List[Story])(implicit neo: Ntx): Unit = {
		val oldLayer = target.layerBelow
		val oldNarration = oldLayer map (Coin.apply(_).story match {
			case Story.Asset(s, o, m, _) => Story.Asset(s, o, m)
			case other => other
		})

		if (oldNarration === narration) {
			Util.updateDates(target, changed = false)
		}
		else {
			Util.updateDates(target, changed = true)
			val newLayer = replaceLayer(oldLayer, oldNarration, narration)
			newLayer.headOption.foreach { head => target describes_= head }
		}
	}
}
