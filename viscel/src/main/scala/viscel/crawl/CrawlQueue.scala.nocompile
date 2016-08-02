package viscel.scribe.crawl

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.database._
import viscel.scribe.narration.{More, Normal}

import scala.annotation.tailrec


class CrawlQueue(startLayer: Layer) {

	var layers: List[Layer] = List(startLayer)
	var nodeQueue: List[Node] = Nil

	def isEmpty(): Boolean = layers.isEmpty && nodeQueue.isEmpty
	def drain(): Unit = {
		layers = Nil
		nodeQueue = Nil
	}

	def deque()(implicit ntx: Ntx): Option[Node] = {
		@tailrec
		def loop(): Option[Node] =
			nodeQueue match {
				case node :: tail =>
					nodeQueue = tail
					Some(node)
				case Nil =>
					layers match {
						case layer :: tail =>
							layers = tail
							addBelow(layer, allowRedo = true)
							loop()
						case Nil => None
					}
			}
		loop()
	}

	def redo(node: Node) = nodeQueue ::= node

	def unvisited(node: Node)(implicit ntx: Ntx): Boolean =
		node.hasLabel(label.More) && (node.describes eq null) ||
			(node.hasLabel(label.Asset) && (node.to(rel.blob) eq null))


	def addBelow(layer: Layer, allowRedo: Boolean = false)(implicit ntx: Ntx): Unit = {
		val nodes = layer.nodes
		if (allowRedo && layers.isEmpty && layer.parent.hasLabel(label.More) && !nodes.exists(_.hasLabel(label.More)))
			nodeQueue ::= layer.parent

		val (more, other) = nodes.partition(_.hasLabel(label.More))
		val (normal, special) = more.partition(Codec.load[More](_).policy === Normal)
		val (unvisMore, visMore) = normal.partition(unvisited)
		layers = visMore.map(_.layer) ::: layers
		nodeQueue = other.filter(unvisited) ::: special ::: nodeQueue ::: unvisMore

	}
}
