package viscel.crawler

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.Log
import viscel.database.Implicits.NodeOps
import viscel.database.{label, NeoCodec, Ntx, rel}
import viscel.shared.Story
import viscel.shared.Story.{More, Asset}

import scala.annotation.tailrec
import scala.Predef.ArrowAssoc


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


	def applyNarration(target: Node, narration: List[Story])(implicit neo: Ntx): Boolean = {
		val oldLayer = target.layerBelow
		val newLayer = replaceLayer(oldLayer, narration)
		newLayer.headOption foreach target.describes_=
		oldLayer !== newLayer
	}

	private val dayInMillis = 24L * 60L * 60L * 1000L

	def updateDates(target: Node)(implicit ntx: Ntx): Unit = {
		val time = System.currentTimeMillis()
		target.setProperty("last_run_complete", time)
	}

	def needsRecheck(target: Node)(implicit ntx: Ntx): Boolean = {
		Log.trace(s"calculating recheck for $target")
		val lastRun = target.get[Long]("last_run_complete")
		val time = System.currentTimeMillis()
		lastRun.isEmpty || (time - lastRun.get > dayInMillis)
	}


	def nextHub(start: Node)(implicit ntx: Ntx): Option[Node] = {
		@tailrec
		def go(node: Node): Node =
			node.layerBelow.filter(_.hasLabel(label.More)) match {
				case Nil => node
				case m :: Nil => go(m)
				case _ => node
			}
		start.layerBelow.filter(_.hasLabel(label.More)).lastOption.map(go)
	}

	def previousMore(start: Option[Node])(implicit ntx: Ntx): Option[(Node, More)] = start match {
		case None => None
		case Some(node) if node.hasLabel(label.More) => Some(node -> NeoCodec.load[More](node))
		case Some(other) => previousMore(other.prev)
	}
}
