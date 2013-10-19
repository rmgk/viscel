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

class ChapterNode(val self: Node) extends NodeContainer[ElementNode] with ContainableNode[ChapterNode] {

	def selfLabel = label.Chapter
	def makeChild = ElementNode(_)
	def makeSelf = ChapterNode(_)

	def name = Neo.txs { self[String]("name") }

	def collection = Neo.txs { self.to(rel.parent).map { CollectionNode(_) }.get }

	def dropLast(origin: String): Unit = Neo.txs {
		last match {
			case Some(ln) if ln.origin == origin =>
				ln.prev.foreach { pn =>
					self.createRelationshipTo(pn.self, rel.last)
				}
				ln.deleteNode()
				dropLast(origin)
			case _ => ()
		}
	}

	def apply(k: String) = Neo.txs { self[String](k) }
	def get(k: String) = Neo.txs { self.get[String](k) }

	override def toString = s"$selfLabel(${collection.name}, $name)"
}

object ChapterNode {
	def apply(node: Node) = new ChapterNode(node)
	def apply(id: String) = Neo.node(label.Chapter, "id", id).map { new ChapterNode(_) }

	def create(name: String, props: (String, Any)*) = ChapterNode(Neo.create(label.Chapter, ("name" -> name) +: props: _*))
}
