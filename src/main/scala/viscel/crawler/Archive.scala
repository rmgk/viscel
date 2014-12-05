package viscel.crawler

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.Log
import viscel.database.Implicits.NodeOps
import viscel.database.{Ntx, rel}
import viscel.shared.Story
import viscel.store.Coin

import scala.annotation.tailrec
import scala.collection.mutable

object Archive {

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
			val below = list.map(_.describes).filter(null.ne).flatMap(_.layer)
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
			updateDates(target, changed = false)
		}
		else {
			updateDates(target, changed = true)
			val newLayer = replaceLayer(oldLayer, oldNarration, narration)
			newLayer.headOption.foreach { head => target describes_= head }
		}
	}


	def updateDates(target: Node, changed: Boolean)(implicit ntx: Ntx): Unit = {
		val time = System.currentTimeMillis()
		val dayInMillis = 24L * 60L * 60L * 1000L
		val lastUpdateOption = target.get[Long]("last_update")
		val newCheckInterval: Long = (for {
			lastUpdate <- lastUpdateOption
			lastCheck <- target.get[Long]("last_check")
			checkInterval <- target.get[Long]("check_interval")
		} yield {
			math.round(checkInterval * (if (changed) 0.8 else 1.2))
		}).getOrElse(dayInMillis)
		val hasUpdated = changed || lastUpdateOption.isEmpty
		Log.trace(s"update dates on $target: time: $time, interval: $newCheckInterval has update: $hasUpdated")
		target.setProperty("last_check", time)
		if (hasUpdated) {
			target.setProperty("last_update", time)
		}
		target.setProperty("check_interval", newCheckInterval)
	}

	def needsRecheck(target: Node)(implicit ntx: Ntx): Boolean = {
		Log.trace(s"calculating recheck for $target")
		val res = for {
			lastCheck <- target.get[Long]("last_check")
			lastUpdate <- target.get[Long]("last_update")
			checkInterval <- target.get[Long]("check_interval")
		} yield {
			val time = System.currentTimeMillis()
			Log.trace(s"recheck $target: $time - $lastCheck > $checkInterval")
			time - lastCheck > checkInterval
		}
		res.getOrElse {
			Log.debug(s"defaulting to recheck because of missing data on $target")
			true
		}
	}
}
