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

class ElementNode(val self: Node) {
	require(Neo.txs { self.getLabels.exists(_ == labelElement) })

	def nid = Neo.txs { self.getId }
	def next: Option[ElementNode] = Neo.txs { self.to("next").map { ElementNode(_) } }
	def prev: Option[ElementNode] = Neo.txs { self.from("next").map { ElementNode(_) } }

	def collection: CollectionNode = Neo.txs { CollectionNode(self.getSingleRelationship("parent", Direction.OUTGOING).getEndNode) }
	def position: Int = Neo.txs { self[Int]("position") }
	def distanceToLast: Int = Neo.txs { collection.last.get.position - position }

	def apply[T](k: String) = Neo.txs { self[T](k) }
	def get[T](k: String) = Neo.txs { self.get[T](k) }

	// def toElement = Neo.txs {
	// 	Element(
	// 		blob = self[String]("blob"),
	// 		mediatype = self[String]("mediatype"),
	// 		source = self[String]("source"),
	// 		origin = self[String]("origin"),
	// 		alt = self.get[String]("alt"),
	// 		title = self.get[String]("title"),
	// 		width = self.get[Int]("width"),
	// 		height = self.get[Int]("height"))
	// }
}

object ElementNode {
	def apply(node: Node) = new ElementNode(node)
	def apply(nodeId: Long) = new ElementNode(Neo.tx { _.getNodeById(nodeId) })

	def create(attributes: (String, Any)*) = ElementNode(Neo.create(labelElement, attributes: _*))
}
