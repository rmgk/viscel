package viscel

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.{Direction, Node, Relationship, RelationshipType}

import scala.annotation.tailrec
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

package object database extends StrictLogging {

	implicit class NodeOps(val self: Node) extends AnyVal {
		def prop[T](key: String)(implicit neo: Ntx): T = self.getProperty(key).asInstanceOf[T]
		def get[T](key: String)(implicit neo: Ntx): Option[T] = Option(self.getProperty(key, null).asInstanceOf[T])
		def to(rel: RelationshipType)(implicit neo: Ntx): Node = self.getSingleRelationship(rel, Direction.OUTGOING) match {
			case null => null
				case other => other.getEndNode
		}
		def to_=(rel: RelationshipType, other: Node)(implicit neo: Ntx): Relationship = {
			logger.trace(s"create rel: $self -$rel-> $other")
			outgoing(rel).foreach(_.delete())
			self.createRelationshipTo(other, rel)
		}
		def from(rel: RelationshipType)(implicit neo: Ntx): Node = self.getSingleRelationship(rel, Direction.INCOMING) match {
			case null => null
			case other => other.getStartNode
		}
		def from_=(rel: RelationshipType, other: Node)(implicit neo: Ntx): Relationship = {
			logger.trace(s"create rel: $self <-$rel- $other")
			incoming(rel).foreach(_.delete())
			other.createRelationshipTo(self, rel)
		}

		def outgoing(rel: RelationshipType)(implicit neo: Ntx): Iterable[Relationship] = iterableAsScalaIterableConverter(self.getRelationships(rel, Direction.OUTGOING)).asScala
		def incoming(rel: RelationshipType)(implicit neo: Ntx): Iterable[Relationship] = iterableAsScalaIterableConverter(self.getRelationships(rel, Direction.INCOMING)).asScala

		def describes_=(other: Node)(implicit neo: Ntx): Relationship = to_=(rel.describes, other)

		def narc_=(other: Node)(implicit neo: Ntx): Relationship = to_=(rel.narc, other)

		def narc(implicit neo: Ntx): Node = to(rel.narc)

		def parc(implicit neo: Ntx): Node = from(rel.describes)

		def below(implicit neo: Ntx): Node = to(rel.describes)

		def above(implicit neo: Ntx): Node = from(rel.describes)

		def layerFirst(implicit neo: Ntx): Node = {
			@tailrec def run(node: Node): Node =
				node.parc match {
					case null => node
					case other => run(other)
				}
			run(self)
		}

		def layerLast(implicit neo: Ntx): Node = {
			@tailrec def run(node: Node): Node =
				node.narc match {
					case null => node
					case other => run(other)
				}
			run(self)
		}

		def layerAbove(implicit neo: Ntx): Option[Node] =
			layerFirst.above match {
				case null => None
				case other => Some(other)
			}

		def rightmost(implicit neo: Ntx): Node = {
			@tailrec def run(node: Node): Node = {
				val end = node.layerLast
				end.below match {
					case null => end
					case other => run(other)
				}
			}
			run(self)
		}

		def origin(implicit neo: Ntx): Node = {
			@tailrec def run(node: Node): Node = {
				val start = node.layerFirst
				start.above match {
					case null => node
					case other => run(other)
				}
			}
			run(self)
		}


		def prev(implicit neo: Ntx): Option[Node] =
			parc match {
				case null => Option(above)
				case other =>
					other.below match {
						case null => Some(other)
						case third => Some(third.rightmost)
					}
			}

		def next(implicit neo: Ntx): Option[Node] =
			below match {
				case null => narc match {
					case null => layerAbove.flatMap(a => Option(a.narc))
					case other => Some(other)
				}
				case other => Some(other)
			}


		def layer(implicit neo: Ntx): List[Node] = {
			@tailrec
			def layerAcc(current: Node, acc: List[Node]): List[Node] = narc match {
				case null => current :: acc
				case next => layerAcc(next, current :: acc)
			}
			layerAcc(self, Nil).reverse
		}

		def layerBelow(implicit neo: Ntx): List[Node] =
			below match {
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