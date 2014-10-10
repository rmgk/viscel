package viscel.store.archive

import org.neo4j.graphdb.Node

import scala.annotation.tailrec

object Traversal {

	@tailrec
	private def layerBegin(node: Node): Node = node.from(rel.narc) match {
		case None => node
		case Some(prev) => layerBegin(prev)
	}

	@tailrec
	private def layerEnd(node: Node): Node = node.to(rel.narc) match {
		case None => node
		case Some(next) => layerEnd(next)
	}

	@tailrec
	private def uppernext(node: Node): Option[Node] = {
		layerBegin(node).from(rel.describes) match {
			case None => None
			case Some(upper) => upper.to(rel.narc) match {
				case None => uppernext(upper)
				case result@Some(_) => result
			}
		}
	}

	@tailrec
	private def rightmost(node: Node): Node = {
		val end = layerEnd(node)
		end.to(rel.describes) match {
			case None => end
			case Some(lower) => rightmost(lower)
		}
	}

	@tailrec
	def origin(node: Node): Node = {
		val begin = Traversal.layerBegin(node)
		begin.from(rel.describes) match {
			case None => begin
			case Some(upper) => origin(upper)
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

	def next(node: Node): Option[Node] =
		node.to(rel.describes).orElse(node.to(rel.narc)).orElse(uppernext(node))

	@tailrec
	final def findBackward[R](p: Node => Option[R])(node: Node): Option[R] = p(node) match {
		case None => prev(node) match {
			case None => None
			case Some(prevNode) => findBackward(p)(prevNode)
		}
		case found@Some(_) => found
	}

	@tailrec
	final def findForward[R](p: Node => Option[R])(node: Node): Option[R] = p(node) match {
		case None => next(node) match {
			case None => None
			case Some(nextNode) => findForward(p)(nextNode)
		}
		case found@Some(_) => found
	}

	def layer(start: Node): List[Node] = {
		@tailrec
		def layerAcc(current: Node, acc: List[Node]): List[Node] = current.to(rel.narc) match {
			case None => current :: acc
			case Some(next) => layerAcc(next, current :: acc)
		}
		layerAcc(start, Nil).reverse
	}

	def layerBelow(above: Node): List[Node] = above.to(rel.describes).fold[List[Node]](Nil)(layer)

}
