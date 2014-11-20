package viscel.crawler

import org.neo4j.graphdb.Node
import viscel.database.Ntx

trait Strategy {
	def run(implicit ntx: Ntx): Option[(Node, Strategy)]
}
