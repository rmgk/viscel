package viscel.database

import org.neo4j.graphdb.{Direction, Node, Relationship, RelationshipType}
import viscel.Log

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

		def describes_=(other: Node)(implicit neo: Ntx): Relationship = to_=(rel.describes, other)

		def narc_=(other: Node)(implicit neo: Ntx): Relationship = to_=(rel.narc, other)

		def narc(implicit neo: Ntx): Node = to(rel.narc)

		def parc(implicit neo: Ntx): Node = from(rel.narc)

		def describes(implicit neo: Ntx): Node = to(rel.describes)

		def above(implicit neo: Ntx): Node = from(rel.describes)

		@tailrec
		def layerFirst(implicit neo: Ntx): Node =
			parc match {
				case null => self
				case other => other.layerFirst
			}

		@tailrec
		def layerLast(implicit neo: Ntx): Node =
			narc match {
				case null => self
				case other => other.layerLast
			}

		def layerAbove(implicit neo: Ntx): Option[Node] = Option(layerFirst.above)

		@tailrec
		def nextAbove(implicit neo: Ntx): Option[Node] =
			layerFirst.above match {
				case null => None
				case other => other.narc match {
					case null => other.nextAbove
					case third => Some(third)
				}
			}

		@tailrec
		def rightmost(implicit neo: Ntx): Node = {
			val end = self.layerLast
			end.describes match {
				case null => end
				case other => other.rightmost
			}
		}

		@tailrec
		def origin(implicit neo: Ntx): Node = {
			val start = self.layerFirst
			start.above match {
				case null => start
				case other => other.origin
			}
		}

		def prev(implicit neo: Ntx): Option[Node] =
			parc match {
				case null => Option(above)
				case other =>
					other.describes match {
						case null => Some(other)
						case third => Some(third.rightmost)
					}
			}

		def next(implicit neo: Ntx): Option[Node] =
			describes match {
				case null => narc match {
					case null => nextAbove
					case other => Some(other)
				}
				case other => Some(other)
			}

		def layer(implicit neo: Ntx): List[Node] = {
			@tailrec
			def layerAcc(current: Node, acc: List[Node]): List[Node] = {
				current.narc match {
					case null => current :: acc
					case nnode => layerAcc(nnode, current :: acc)
				}
			}
			layerAcc(self, Nil).reverse
		}

		def layerBelow(implicit neo: Ntx): List[Node] =
			describes match {
				case null => Nil
				case other => other.layer
			}

		def fold[S](state: S)(f: S => Node => S)(implicit neo: Ntx): S = {
			@tailrec
			def run(state: S, node: Node, f: S => Node => S): S = {
				val nextState = f(state)(node)
				node.next match {
					case None => nextState
					case Some(nextNode) => run(nextState, nextNode, f)
				}
			}
			run(state, self, f)
		}


	}
}