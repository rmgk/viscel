package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import util.Try

class ElementNode(val self: Node) extends {
	val selfLabel = label.Element
} with ViscelNode with ContainableNode[ElementNode] {

	def makeSelf = ElementNode(_)

	def collection: CollectionNode = Neo.txs { CollectionNode(self.getSingleRelationship(rel.parent, Direction.OUTGOING).getEndNode) }
	def distanceToLast: Int = Neo.txs { collection.last.get.position - position }

	def apply[T](k: String) = Neo.txs { self[T](k) }
	def get[T](k: String) = Neo.txs { self.get[T](k) }

	def delete() = {}

}

object ElementNode {
	def apply(node: Node) = new ElementNode(node)
	def apply(nodeId: Long) = new ElementNode(Neo.tx { _.getNodeById(nodeId) })

	def create(attributes: (String, Any)*) = ElementNode(Neo.create(label.Element, attributes: _*))
}
