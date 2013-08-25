package viscel

import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.RelationshipType
import scala.language.implicitConversions

package object store {

	implicit def stringToRelationshipType(name: String) = DynamicRelationshipType.withName(name)

	implicit class NodeOps(node: Node) {
		def apply[T](key: String) = node.getProperty(key).asInstanceOf[T]
		def get[T](key: String) = Option(node.getProperty(key, null).asInstanceOf[T])
		def to(rel: RelationshipType) = Option(node.getSingleRelationship(rel, Direction.OUTGOING)).map { _.getEndNode }
		def from(rel: RelationshipType) = Option(node.getSingleRelationship(rel, Direction.INCOMING)).map { _.getStartNode }
		def outgoing(rel: RelationshipType) = node.getRelationships(rel, Direction.OUTGOING)
		def incoming(rel: RelationshipType) = node.getRelationships(rel, Direction.INCOMING)
		// def get[T](key: String, default: T) = node.getProperty(key, default).asInstanceOf[T]
	}

	implicit class RelationshipOps(rel: Relationship) {
		def apply[T](key: String) = rel.getProperty(key).asInstanceOf[T]
		def get[T](key: String) = Option(rel.getProperty(key, null).asInstanceOf[T])
		// def get[T](key: String, default: T) = rel.getProperty(key, default).asInstanceOf[T]
	}

}
