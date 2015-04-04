package viscel.scribe.database

import org.neo4j.graphdb.Node
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.narration.{More, Story, Volatile}

import scala.annotation.tailrec

object Archive {

	def deleteRecursive(nodes: List[Node])(implicit ntx: Ntx): Unit =
		nodes foreach (_.fold(()) { _ => n =>
			Option(n.to(rel.blob)).foreach(ntx.delete)
			ntx.delete(n)
		})


	def nextHub(start: Node)(implicit ntx: Ntx): Option[Node] = {
		@tailrec
		def go(node: Node, saved: Node): Node = {

			Codec.load[Story](node) match {
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
		}
		start.layer.nodes.find(_.hasLabel(label.More)).map(n => go(n, n))
	}

	def parentMore(start: Option[Node])(implicit ntx: Ntx): Option[(Node, More)] = start.flatMap(_.above).flatMap { n =>
		if (n.hasLabel(label.More)) Some((n, Codec.load[More](n)))
		else None
	}

	def collectMore(start: Node)(implicit ntx: Ntx): List[More] = start.fold(List[More]())(s => n =>
		if (n.hasLabel(label.More)) Codec.load[More](n) :: s
		else s)
}
