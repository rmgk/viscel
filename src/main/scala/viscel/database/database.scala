package viscel

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.{Direction, Node, Relationship, RelationshipType}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

package object database  extends StrictLogging {

	implicit class NodeOps(val node: Node) extends AnyVal {
		def prop[T](key: String)(implicit neo: Ntx): T = node.getProperty(key).asInstanceOf[T]
		def get[T](key: String)(implicit neo: Ntx): Option[T] = Option(node.getProperty(key, null).asInstanceOf[T])
		def to(rel: RelationshipType)(implicit neo: Ntx): Option[Node] = Option(node.getSingleRelationship(rel, Direction.OUTGOING)).map { _.getEndNode }
		def to_=(rel: RelationshipType, other: Node)(implicit neo: Ntx): Relationship = {
			logger.trace(s"create rel: $node -$rel-> $other")
			outgoing(rel).foreach(_.delete())
			node.createRelationshipTo(other, rel)
		}
		def from(rel: RelationshipType)(implicit neo: Ntx): Option[Node] = Option(node.getSingleRelationship(rel, Direction.INCOMING)).map { _.getStartNode }
		def from_=(rel: RelationshipType, other: Node)(implicit neo: Ntx): Relationship = {
			logger.trace(s"create rel: $node <-$rel- $other")
			incoming(rel).foreach(_.delete())
			other.createRelationshipTo(node, rel)
		}
		def outgoing(rel: RelationshipType)(implicit neo: Ntx): Iterable[Relationship] = iterableAsScalaIterableConverter(node.getRelationships(rel, Direction.OUTGOING)).asScala
		def incoming(rel: RelationshipType)(implicit neo: Ntx): Iterable[Relationship] = iterableAsScalaIterableConverter(node.getRelationships(rel, Direction.INCOMING)).asScala
	}

	implicit class RelationshipOps(val rel: Relationship) extends AnyVal {
		def apply[T](key: String)(implicit neo: Ntx): T = rel.getProperty(key).asInstanceOf[T]
		def get[T](key: String)(implicit neo: Ntx): Option[T] = Option(rel.getProperty(key, null).asInstanceOf[T])
	}

}
