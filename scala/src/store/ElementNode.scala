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

	def chapter: ChapterNode = Neo.txs { self.to(rel.parent).map { ChapterNode(_) }.get }
	def collection: CollectionNode = Neo.txs { chapter.collection }

	override def next = super.next.orElse { chapter.next.flatMap { _.last } }
	override def prev = super.prev.orElse { chapter.prev.flatMap { _.last } }

	def distanceToLast: Int = Neo.txs {
		def countFrom(cno: Option[ChapterNode], acc: Int): Int = cno match {
			case Some(cn) => countFrom(cn.next, cn.size + acc)
			case None => acc
		}
		chapter.size - position + countFrom(chapter.next, 0)
	}

	def apply[T](k: String) = Neo.txs { self[T](k) }
	def get[T](k: String) = Neo.txs { self.get[T](k) }
}

object ElementNode {
	def apply(node: Node) = new ElementNode(node)
	def apply(nodeId: Long) = new ElementNode(Neo.tx { _.getNodeById(nodeId) })

	def create(attributes: (String, Any)*) = ElementNode(Neo.create(label.Element, attributes: _*))
}
