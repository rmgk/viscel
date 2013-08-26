package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import util.Try
import viscel.time

abstract class AbstractCollectionNode[ChildType <: ContainableNode[ChildType]](val self: Node) extends NodeContainer[ChildType] {
	require(Neo.txs { self.getLabels.exists(_ == label.Collection) })

	def nid = Neo.txs { self.getId }
	def id = Neo.txs { self[String]("id") }
	def name = Neo.txs { self.get[String]("name").getOrElse(id) }

	override def equals(other: Any) = other match {
		case o: AbstractCollectionNode[ChildType] => self == o.self
		case _ => false
	}

	override def toString = s"Collection($id)"

}

class CollectionNode(self: Node) extends AbstractCollectionNode[ElementNode](self) {
	def containRelation = rel.parent
	def makeChild = ElementNode(_)
}

object CollectionNode {
	def apply(node: Node) = new CollectionNode(node)
	def apply(id: String) = Neo.node(label.Collection, "id", id).map { new CollectionNode(_) }

	def create(id: String, name: Option[String] = None) = CollectionNode(Neo.create(label.Collection, (Seq("id" -> id) ++ name.map { "name" -> _ }): _*))
}
