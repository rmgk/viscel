package viscel

import org.neo4j.graphdb.{Direction, Node, Relationship, RelationshipType}

import scala.collection.JavaConverters._

package object store {

	implicit class NodeOps(node: Node) {
		def apply[T](key: String): T = node.getProperty(key).asInstanceOf[T]
		def get[T](key: String): Option[T] = Option(node.getProperty(key, null).asInstanceOf[T])
		def to(rel: RelationshipType): Option[Node] = Option(node.getSingleRelationship(rel, Direction.OUTGOING)).map { _.getEndNode }
		def to_=(rel: RelationshipType, other: Node): Relationship = {
			outgoing(rel).foreach(_.delete())
			node.createRelationshipTo(other, rel)
		}
		def from(rel: RelationshipType): Option[Node] = Option(node.getSingleRelationship(rel, Direction.INCOMING)).map { _.getStartNode }
		def from_=(rel: RelationshipType, other: Node): Relationship = {
			incoming(rel).foreach(_.delete)
			other.createRelationshipTo(node, rel)
		}
		def outgoing(rel: RelationshipType): Iterable[Relationship] = node.getRelationships(rel, Direction.OUTGOING).asScala
		def incoming(rel: RelationshipType): Iterable[Relationship] = node.getRelationships(rel, Direction.INCOMING).asScala
	}

	implicit class RelationshipOps(rel: Relationship) {
		def apply[T](key: String): T = rel.getProperty(key).asInstanceOf[T]
		def get[T](key: String): Option[T] = Option(rel.getProperty(key, null).asInstanceOf[T])
	}

}
