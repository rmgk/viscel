package viscel.store

import org.neo4j.graphdb.Node

import scala.annotation.tailrec

object Traversal {

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

}
