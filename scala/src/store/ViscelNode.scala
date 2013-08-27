package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import util.Try
import viscel.time
import viscel._

trait ViscelNode {
	def self: Node
	def selfLabel: Label
	def nid = Neo.txs { self.getId }

	require(Neo.txs { self.getLabels.exists(_ == selfLabel) }, s"node label did not match $selfLabel")

	override def equals(other: Any) = other match {
		case o: ViscelNode => self == o.self
		case _ => false
	}

	override def hashCode: Int = self.hashCode

	override def toString: String = s"${selfLabel.name}($nid)"
}

object ViscelNode {
	def apply(node: Node): Try[ViscelNode] = Neo.txs { node.getLabels.toList } match {
		case List() => failure(s"node has no label")
		case List(l) if l == label.Chapter => Try(ChapterNode(node))
		case List(l) if l == label.Collection => Try(CollectionNode(node))
		case List(l) if l == label.ChapteredCollection => Try(ChapteredCollectionNode(node))
		//case List(l) if l == label.Config => Try(ConfigNode(node))
		case List(l) if l == label.Element => Try(ElementNode(node))
		case List(l) if l == label.User => Try(UserNode(node))
		case List(l) => failure(s"unhandled label $l")
		case list @ List(_) => failure(s"to many labels $list")
	}
	def apply(id: Long): Try[ViscelNode] = Try { Neo.tx { _.getNodeById(id) } }.flatMap { apply }
}