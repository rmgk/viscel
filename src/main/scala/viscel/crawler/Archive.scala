package viscel.crawler

import org.neo4j.graphdb.Node
import org.scalactic.Equality
import org.scalactic.TypeCheckedTripleEquals._
import viscel.Log
import viscel.database.Implicits.NodeOps
import viscel.database.{NeoCodec, Ntx, rel}
import viscel.shared.Story
import viscel.shared.Story.Asset

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

	private def normalize: Story => Story = {
		case a@Asset(_, _, _, Some(_)) => a.copy(blob = None)
		case other => other
	}

	private def replaceLayer(oldLayer: List[Node], newNarration: List[Story])(implicit neo: Ntx): List[Node] = {
		val oldNarration: List[Story] = oldLayer map { n => normalize(NeoCodec.load[Story](n)) }
		var oldMap: List[(Story, Node)] = List(oldNarration zip oldLayer: _*)
		val newLayer: List[Node] = newNarration.map { story =>
			val nstory = normalize(story)
			oldMap.span(_._1 != nstory) match {
				case (left, Nil) => NeoCodec.create(story)
				case (left, (_, oldNode) :: rest) =>
					oldMap = left ::: rest
					oldNode
			}
		}
		deleteRecursive(oldMap.map(_._2))
		connectLayer(newLayer)
		newLayer
	}


	def applyNarration(target: Node, narration: List[Story])(implicit neo: Ntx): List[Node] = {
		val oldLayer = target.layerBelow
		val newLayer = replaceLayer(oldLayer, narration)
		newLayer.headOption foreach target.describes_=
		updateDates(target, changed = oldLayer !== newLayer)
		newLayer
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
