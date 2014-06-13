package viscel.store

import org.neo4j.graphdb.Node
import scala.annotation.tailrec
import scala.language.implicitConversions

class ElementNode(val self: Node) extends ViscelNode {
	def selfLabel = label.Element

	def chapter: ChapterNode = Neo.txs { self.to(rel.parent).map { ChapterNode(_) }.get }
	def chapter_=(cn: ChapterNode) = Neo.txs { self.to_=(rel.parent, cn.self) }
	def collection: CollectionNode = Neo.txs { chapter.collection }

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
	//	def nextView: Option[ElementNode] = {
	//		def firstfirst(chap: ChapterNode): Option[ElementNode] = chap.first.orElse { chap.next.flatMap { firstfirst } }
	//		next.orElse { chapter.next.flatMap { firstfirst } }
	//	}
	//	def prevView: Option[ElementNode] = {
	//		def firstlast(chap: ChapterNode): Option[ElementNode] = chap.last.orElse { chap.prev.flatMap { firstlast } }
	//		prev.orElse { chapter.prev.flatMap { firstlast } }
	//	}

	//	override def deleteNode(warn: Boolean = true) = Neo.txs {
	//		self.incoming(rel.bookmarks).foreach { rel =>
	//			val bmn = rel.getStartNode
	//			bmn.setProperty("chapter", chapter.position)
	//			bmn.setProperty("page", position)
	//		}
	//		super.deleteNode(warn)
	//	}
	//
	//	def distanceToLast: Int = Neo.txs {
	//		def countFrom(cno: Option[ChapterNode], acc: Int): Int = cno match {
	//			case Some(cn) => countFrom(cn.next, cn.size + acc)
	//			case None => acc
	//		}
	//		chapter.size - position + countFrom(chapter.next, 0)
	//	}

	def apply[T](k: String) = Neo.txs { self[T](k) }
	def get[T](k: String) = Neo.txs { self.get[T](k) }
	def origin = Neo.txs { self[String]("origin") }

	override def toString = s"$selfLabel(${collection.name})"
}

object ElementNode {
	def apply(node: Node) = new ElementNode(node)
	def apply(nodeId: Long) = new ElementNode(Neo.tx { _.getNodeById(nodeId) })

	def create(attributes: (String, Any)*) = ElementNode(Neo.create(label.Element, attributes: _*))
}
