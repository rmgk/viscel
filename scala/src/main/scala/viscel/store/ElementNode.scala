package viscel.store

import org.neo4j.graphdb.Node
import viscel.core.AbsUri

import scala.annotation.tailrec
import scala.language.implicitConversions

class ElementNode(val self: Node) extends ViscelNode {
	def selfLabel = label.Element

	def chapter: ChapterNode = Neo.txs { self.to(rel.parent).map { ChapterNode(_) }.get }
	def chapter_=(cn: ChapterNode) = Neo.txs { self.to_=(rel.parent, cn.self) }
	def collection: CollectionNode = Neo.txs { chapter.collection }

	def blob: Option[BlobNode] = Neo.txs { self.to(rel.blob).map { BlobNode(_) } }
	def blob_=(bn: BlobNode) = Neo.txs { self.to_=(rel.blob, bn.self) }

	def next: Option[ElementNode] = Neo.txs { self.to(rel.next).map { ElementNode(_) } }
	def next_=(en: ElementNode) = Neo.txs { self.to_=(rel.next, en.self) }
	def prev: Option[ElementNode] = Neo.txs { self.from(rel.next).map { ElementNode(_) } }
	def prev_=(en: ElementNode) = Neo.txs { self.from_=(rel.next, en.self) }

	def position: Int = Neo.txs {
		@tailrec
		def pos(oen: Option[ElementNode], n: Int): Int = oen match {
			case None => n
			case Some(en) => pos(en.prev, n + 1)
		}
		pos(prev, 1)
	}

	def distanceToLast: Int = Neo.txs {
		@tailrec
		def dist(oen: Option[ElementNode], n: Int): Int = oen match {
			case None => n
			case Some(en) => dist(en.next, n + 1)
		}
		dist(next, 0)
	}

	//	@deprecated("use proper accessors", "5.0.0")
	//	def apply[T](k: String) = Neo.txs { self[T](k) }
	//	@deprecated("use proper accessors", "5.0.0")
	//	def get[T](k: String) = Neo.txs { self.get[T](k) }
	def origin = Neo.txs { AbsUri(self[String]("origin")) }
	def source = Neo.txs { AbsUri(self[String]("source")) }

	override def toString = s"$selfLabel(${ collection.name })"
}

object ElementNode {
	def apply(node: Node) = new ElementNode(node)
	def apply(nodeId: Long) = new ElementNode(Neo.tx { _.getNodeById(nodeId) })

	def create(source: AbsUri, origin: AbsUri, attributes: Map[String, Any]) = ElementNode(
		Neo.create(label.Element, attributes + ("source" -> source.toString) + ("origin" -> origin.toString)))
}
