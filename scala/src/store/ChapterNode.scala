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

class ChapterNode(val self: Node) extends NodeContainer[ElementNode] {
	require(Neo.txs { self.getLabels.exists(_ == label.Chapter) })

	def containRelation = rel.chapter
	def makeChild = ElementNode(_)

	def nid = Neo.txs { self.getId }
	def name = Neo.txs { self.get[String]("name") }

	override def equals(other: Any) = other match {
		case o: ChapterNode => self == o.self
		case _ => false
	}

}

object ChapterNode {
	def apply(node: Node) = new ChapterNode(node)
	def apply(id: String) = Neo.node(label.Collection, "id", id).map { new ChapterNode(_) }

	def create(id: String, name: Option[String] = None) = ChapterNode(Neo.create(label.Collection, (Seq("id" -> id) ++ name.map { "name" -> _ }): _*))
}
