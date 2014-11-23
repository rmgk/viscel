package viscel.crawler

import org.neo4j.graphdb.Node
import viscel.database.Ntx

trait Strategy {
	def run(implicit ntx: Ntx): Option[(Node, Strategy)]
	def andThen(other: Strategy): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = this.run match {
			case Some((node, next)) => Some(node -> next.andThen(other))
			case None => other.run
		}
	}
}
