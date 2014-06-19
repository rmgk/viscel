package viscel.store

import org.neo4j.graphdb.Direction

trait DescribingNode {
	this: ViscelNode =>

	def describes: Option[ArchiveNode] = Neo.txs { self.to { rel.describes }.map { ArchiveNode(_) } }
	def describes_=(archive: Option[ArchiveNode]): Unit = Neo.txs {
		archive match {
			case None => self.getSingleRelationship(rel.describes, Direction.OUTGOING).delete()
			case Some(other) => self.to_=(rel.describes, other.self)
		}
	}
}
