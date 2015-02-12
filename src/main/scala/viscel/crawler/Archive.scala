package viscel.crawler

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.database.Implicits.NodeOps
import viscel.database.{Codec, Ntx, label, rel}
import viscel.shared.{More, Story, Volatile}

import scala.Predef.ArrowAssoc
import scala.annotation.tailrec

object Archive {

	private def connectLayer(layer: List[Node])(implicit neo: Ntx): List[Node] = {
		layer.reduceLeftOption { (prev, next) => prev narc_= next; next }
		layer.lastOption.foreach(_.outgoing(rel.narc).foreach(_.delete()))
		layer.headOption.foreach(_.incoming(rel.narc).foreach(_.delete()))
		layer
	}

	def deleteRecursive(nodes: List[Node])(implicit ntx: Ntx): Unit = nodes foreach (_.fold(())(_ => ntx.delete))

	private def replaceLayer(oldLayer: List[Node], newNarration: List[Story])(implicit neo: Ntx): List[Node] = {
		val oldNarration: List[Story] = oldLayer map { n => Codec.load[Story](n) }
		var oldMap: List[(Story, Node)] = List(oldNarration zip oldLayer: _*)
		val newLayer: List[Node] = newNarration.map { story =>
			oldMap.span(_._1 != story) match {
				case (left, Nil) => Codec.create(story)
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


	def nextHub(start: Node)(implicit ntx: Ntx): Option[Node] = {
		@tailrec
		def go(node: Node, saved: Node): Node = Codec.load[Story](node) match {
			case More(_, Volatile, _) => node
			case More(_, _, _) => node.next match {
				case None => node
				case Some(next) => go(next, node)
			}
			case _ => node.next match {
				case None => saved
				case Some(next) => go(next, saved)
			}
		}
		start.layerBelow.find(_.hasLabel(label.More)).map(n => go(n, n))
	}

	def parentMore(start: Option[Node])(implicit ntx: Ntx): Option[(Node, More)] = start.flatMap(_.above).map(n => (n, Codec.load[More](n)))

	def previousMore(start: Option[Node])(implicit ntx: Ntx): Option[(Node, More)] = start match {
		case None => None
		case Some(node) if node.hasLabel(label.More) => Some(node -> Codec.load[More](node))
		case Some(other) => previousMore(other.prev)
	}

	def collectMore(start: Node)(implicit ntx: Ntx): List[More] = start.fold(List[More]())(s => n =>
		if (n.hasLabel(label.More)) Codec.load[More](n) :: s
		else s)
}
