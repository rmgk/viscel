package viscel.store

import org.neo4j.graphdb.Direction

trait DescribingNode {
	this: Coin =>

	def describes: Option[ArchiveNode] = self.to { rel.describes }.map { ArchiveNode(_) }
	def describes_=(archive: Option[ArchiveNode]): Unit =
		archive match {
			case None => self.getSingleRelationship(rel.describes, Direction.OUTGOING).delete()
			case Some(other) => self.to_=(rel.describes, other.self)
		}
}
