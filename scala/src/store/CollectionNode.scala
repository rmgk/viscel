package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import scala.collection.JavaConversions._
import util.Try
import viscel.time

class CollectionNode(val self: Node) extends NodeContainer[ChapterNode] with ViscelNode {
	def selfLabel = label.Collection
	def makeChild = ChapterNode(_)

	def id = Neo.txs { self[String]("id") }
	def name = Neo.txs { self.get[String]("name").getOrElse(id) }

	def totalSize = Neo.txs { children.map { _.size }.sum }

	override def toString = s"Collection($id)"
}

object CollectionNode {
	def apply(node: Node) = new CollectionNode(node)
	def apply(nodeId: Long) = new CollectionNode(Neo.tx { _.getNodeById(nodeId) })
	def apply(id: String) = Neo.node(label.Collection, "id", id).map { new CollectionNode(_) }

	def create(id: String, name: String) = CollectionNode(Neo.create(label.Collection, "id" -> id, "name" -> name))
}
