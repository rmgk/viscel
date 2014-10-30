package viscel

import org.neo4j.graphdb.{Direction, Node, Relationship, RelationshipType}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

package object database {

	implicit class NodeOps(node: Node) {
		def apply[T](key: String)(implicit neo: Ntx): T = node.getProperty(key).asInstanceOf[T]
		def get[T](key: String)(implicit neo: Ntx): Option[T] = Option(node.getProperty(key, null).asInstanceOf[T])
		def to(rel: RelationshipType)(implicit neo: Ntx): Option[Node] = Option(node.getSingleRelationship(rel, Direction.OUTGOING)).map { _.getEndNode }
		def to_=(rel: RelationshipType, other: Node)(implicit neo: Ntx): Relationship = {
			outgoing(rel).foreach((r: Relationship) => r.delete())
			node.createRelationshipTo(other, rel)
		}
		def from(rel: RelationshipType)(implicit neo: Ntx): Option[Node] = Option(node.getSingleRelationship(rel, Direction.INCOMING)).map { _.getStartNode }
		def from_=(rel: RelationshipType, other: Node)(implicit neo: Ntx): Relationship = {
			incoming(rel).foreach((r: Relationship) => r.delete())
			other.createRelationshipTo(node, rel)
		}
		def outgoing(rel: RelationshipType)(implicit neo: Ntx): Iterable[Relationship] = iterableAsScalaIterableConverter(node.getRelationships(rel, Direction.OUTGOING)).asScala
		def incoming(rel: RelationshipType)(implicit neo: Ntx): Iterable[Relationship] = iterableAsScalaIterableConverter(node.getRelationships(rel, Direction.INCOMING)).asScala
	}

	implicit class RelationshipOps(rel: Relationship) {
		def apply[T](key: String)(implicit neo: Ntx): T = rel.getProperty(key).asInstanceOf[T]
		def get[T](key: String)(implicit neo: Ntx): Option[T] = Option(rel.getProperty(key, null).asInstanceOf[T])
	}

}
