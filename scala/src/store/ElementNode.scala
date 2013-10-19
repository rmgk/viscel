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
import viscel.time

class ElementNode(val self: Node) extends ViscelNode with ContainableNode[ElementNode] {
	def selfLabel = label.Element
	def makeSelf = ElementNode.apply _

	def chapter: ChapterNode = Neo.txs { self.to(rel.parent).map { ChapterNode(_) }.get }
	def collection: CollectionNode = Neo.txs { chapter.collection }

	def nextView: Option[ElementNode] = {
		def firstfirst(chap: ChapterNode): Option[ElementNode] = chap.first.orElse { chap.next.flatMap { firstfirst } }
		next.orElse { chapter.next.flatMap { firstfirst } }
	}
	def prevView: Option[ElementNode] = {
		def firstlast(chap: ChapterNode): Option[ElementNode] = chap.last.orElse { chap.prev.flatMap { firstlast } }
		prev.orElse { chapter.prev.flatMap { firstlast } }
	}

	override def deleteNode(warn: Boolean = true) = Neo.txs {
		self.incoming(rel.bookmarks).foreach { rel =>
			val bmn = rel.getStartNode
			bmn.setProperty("chapter", chapter.position)
			bmn.setProperty("page", position)
		}
		super.deleteNode(warn)
	}

	def distanceToLast: Int = Neo.txs {
		def countFrom(cno: Option[ChapterNode], acc: Int): Int = cno match {
			case Some(cn) => countFrom(cn.next, cn.size + acc)
			case None => acc
		}
		chapter.size - position + countFrom(chapter.next, 0)
	}

	def apply[T](k: String) = Neo.txs { self[T](k) }
	def get[T](k: String) = Neo.txs { self.get[T](k) }
	def origin = Neo.txs { self[String]("origin") }

	override def toString = s"$selfLabel(${collection.name}, ${chapter.position}, $position)"
}

object ElementNode {
	def apply(node: Node) = new ElementNode(node)
	def apply(nodeId: Long) = new ElementNode(Neo.tx { _.getNodeById(nodeId) })

	def create(attributes: (String, Any)*) = ElementNode(Neo.create(label.Element, attributes: _*))
}
