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

abstract class AbstractCollectionNode[ChildType <: ContainableNode[ChildType]](val self: Node, val selfLabel: Label) extends NodeContainer[ChildType] with ViscelNode {

	def id = Neo.txs { self[String]("id") }
	def name = Neo.txs { self.get[String]("name").getOrElse(id) }

	override def toString = s"Collection($id)"

}

class CollectionNode(self: Node) extends AbstractCollectionNode[ElementNode](self, label.Collection) {
	def containRelation = rel.parent
	def makeChild = ElementNode(_)
}

class ChapteredCollectionNode(self: Node) extends AbstractCollectionNode[ChapterNode](self, label.ChapteredCollection) {
	def containRelation = rel.chapter
	def makeChild = ChapterNode(_)
}

object CollectionNode {
	def apply(node: Node) = new CollectionNode(node)
	def apply(id: String) = Neo.node(label.Collection, "id", id).map { new CollectionNode(_) }

	def create(id: String, name: Option[String] = None) = CollectionNode(Neo.create(label.Collection, (Seq("id" -> id) ++ name.map { "name" -> _ }): _*))
}

object ChapteredCollectionNode {
	def apply(node: Node) = new ChapteredCollectionNode(node)
	def apply(id: String) = Neo.node(label.ChapteredCollection, "id", id).map { new ChapteredCollectionNode(_) }

	def create(id: String, name: Option[String] = None) = ChapteredCollectionNode(Neo.create(label.ChapteredCollection, (Seq("id" -> id) ++ name.map { "name" -> _ }): _*))
}
