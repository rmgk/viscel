package viscel.neoadapter.database

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.neoadapter.database.Implicits.NodeOps

import scala.annotation.tailrec

final case class Layer(parent: Node) {
	def isEmpty(implicit neo: Ntx) = parent.describes eq null

	def nodes(implicit ntx: Ntx): List[Node] = {
		@tailrec
		def layerAcc(current: Node, acc: List[Node]): List[Node] = {
			current.narc match {
				case null => current :: acc
				case nnode => layerAcc(nnode, current :: acc)
			}
		}
		parent.describes match {
			case null => Nil
			case node => layerAcc(node, Nil).reverse
		}
	}

	def recursive(implicit ntx: Ntx): List[Node] = {
		@tailrec
		def go(remaining: List[Node], acc: List[Node]): List[Node] = remaining match {
			case Nil => acc
			case h :: t =>
				go(h.layer.nodes ::: t, h :: acc)
		}
		go(nodes, Nil).reverse
	}


	def replace(narration: List[Story])(implicit neo: Ntx): Boolean = {

		def connectLayer(layer: List[Node])(implicit neo: Ntx): Unit = {
			layer.reduceLeftOption { (prev, next) => prev narc_= next; next }
			layer.lastOption.foreach(_.outgoing(rel.narc).foreach(_.delete()))
			layer.headOption.foreach(_.incoming(rel.narc).foreach(_.delete()))
		}

		def replaceLayer(oldLayer: List[Node], newNarration: List[Story])(implicit neo: Ntx): List[Node] = {
			val oldNarration: List[Story] = oldLayer map { n => Codec.load[Story](n) }
			var oldMap: List[(Story, Node)] = oldNarration zip oldLayer
			val newLayer: List[Node] = newNarration.map { story =>
				oldMap.span(_._1 != story) match {
					case (left, Nil) => Codec.create(story)
					case (left, (_, oldNode) :: rest) =>
						oldMap = left ::: rest
						oldNode
				}
			}
			oldMap.foreach(n => n._2.deleteRecursive)
			connectLayer(newLayer)
			newLayer
		}

		val oldLayer = nodes
		val newLayer = replaceLayer(oldLayer, narration)
		newLayer.headOption foreach parent.describes_=
		oldLayer !== newLayer
	}
}
