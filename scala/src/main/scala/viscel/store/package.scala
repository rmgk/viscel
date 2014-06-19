package viscel

import org.neo4j.graphdb.{Direction, Node, Relationship, RelationshipType}

package object store {

	implicit class NodeOps(node: Node) {
		def apply[T](key: String) = { node.getProperty(key).asInstanceOf[T] }
		def get[T](key: String) = { Option(node.getProperty(key, null).asInstanceOf[T]) }
		def to(rel: RelationshipType) = { Option(node.getSingleRelationship(rel, Direction.OUTGOING)).map { _.getEndNode } }
		def to_=(rel: RelationshipType, other: Node) = {
			val iterator = outgoing(rel).iterator()
			while(iterator.hasNext()) iterator.next().delete()
			node.createRelationshipTo(other, rel)
		}
		def from(rel: RelationshipType): Option[Node] = { Option(node.getSingleRelationship(rel, Direction.INCOMING)).map { _.getStartNode } }
		def from_=(rel: RelationshipType, other: Node) = {
			val iterator = incoming(rel).iterator()
			while(iterator.hasNext()) iterator.next().delete()
			other.createRelationshipTo(node, rel)
		}
		def outgoing(rel: RelationshipType) = { node.getRelationships(rel, Direction.OUTGOING) }
		def incoming(rel: RelationshipType) = { node.getRelationships(rel, Direction.INCOMING) }
	}

	implicit class RelationshipOps(rel: Relationship) {
		def apply[T](key: String): T = rel.getProperty(key).asInstanceOf[T]
		def get[T](key: String): Option[T] = Option(rel.getProperty(key, null).asInstanceOf[T])
	}

}
