package viscel.scribe.database

import org.neo4j.graphdb.{Direction, Node, Relationship, RelationshipType}
import viscel.scribe.Log

import scala.annotation.tailrec
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

object Implicits {

	final implicit class NodeOps(val self: Node) extends AnyVal {
		def prop[T](key: String)(implicit neo: Ntx): T = self.getProperty(key).asInstanceOf[T]
		def get[T](key: String)(implicit neo: Ntx): Option[T] = Option(self.getProperty(key, null).asInstanceOf[T])
		def to(rel: RelationshipType)(implicit neo: Ntx): Node = self.getSingleRelationship(rel, Direction.OUTGOING) match {
			case null => null
			case other => other.getEndNode
		}
		def to_=(rel: RelationshipType, other: Node)(implicit neo: Ntx): Relationship = {
			Log.trace(s"create rel: $self -$rel-> $other")
			outgoing(rel).foreach(_.delete())
			self.createRelationshipTo(other, rel)
		}
		def from(rel: RelationshipType)(implicit neo: Ntx): Node = self.getSingleRelationship(rel, Direction.INCOMING) match {
			case null => null
			case other => other.getStartNode
		}
		def from_=(rel: RelationshipType, other: Node)(implicit neo: Ntx): Relationship = {
			Log.trace(s"create rel: $self <-$rel- $other")
			incoming(rel).foreach(_.delete())
			other.createRelationshipTo(self, rel)
		}

		def outgoing(rel: RelationshipType)(implicit neo: Ntx): Iterable[Relationship] = iterableAsScalaIterableConverter(self.getRelationships(rel, Direction.OUTGOING)).asScala
		def incoming(rel: RelationshipType)(implicit neo: Ntx): Iterable[Relationship] = iterableAsScalaIterableConverter(self.getRelationships(rel, Direction.INCOMING)).asScala

		def display(implicit ntx: Ntx): String = s"($parc, $describing) -> $self -> ($narc, $describes)"

		def describes_=(other: Node)(implicit neo: Ntx): Relationship = to_=(rel.describes, other)

		def narc_=(other: Node)(implicit neo: Ntx): Relationship = to_=(rel.narc, other)

		def narc(implicit neo: Ntx): Node = to(rel.narc)

		def parc(implicit neo: Ntx): Node = from(rel.narc)

		def describes(implicit neo: Ntx): Node = to(rel.describes)

		def describing(implicit neo: Ntx): Node = from(rel.describes)


		@tailrec
		def above(implicit neo: Ntx): Option[Node] = parc match {
			case null => Option(self.describing)
			case other => other.above
		}

		def layer: Layer = new Layer(self)

		def deleteRecursive(implicit ntx: Ntx): Unit =
			(self :: self.layer.recursive).foreach { n =>
				Option(n.to(rel.blob)).foreach(ntx.delete)
				ntx.delete(n)
			}


	}
}
