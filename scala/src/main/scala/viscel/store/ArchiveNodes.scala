package viscel.store

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.core._

import scala.collection.JavaConversions._

trait ArchiveNode extends ViscelNode {
	def flatten = ArchiveNode.foldChildren(Seq[ViscelNode](), this) {
		case (acc, pn: PageNode) => acc
		case (acc, sn: StructureNode) => sn.payload match {
			case Some(vn) => acc :+ vn
			case None => acc
		}
	}
}

object ArchiveNode {
	def apply(node: Node): ArchiveNode = Neo.txs { node.getLabels.toList } match {
		case List(l) if l === label.Page => PageNode(node)
		case List(l) if l === label.Structure => StructureNode(node)
	}
	def apply(id: Long): ArchiveNode = apply(Neo.tx { _.getNodeById(id) })
	def apply(cn: CollectionNode): Option[ArchiveNode] = Neo.txs { cn.self.to(rel.archive).map { apply } }

	def foldChildren[A] = fold[A](nextFirst = false) _
	def foldNext[A] = fold[A](nextFirst = true) _

	def fold[A](nextFirst: Boolean)(acc: A, an: ArchiveNode)(op: (A, ArchiveNode) => A): A = {
		val res = op(acc, an)
		an match {
			case pn: PageNode => pn.describes.fold(ifEmpty = res)(desc => fold(nextFirst)(res, desc)(op))
			case sn: StructureNode =>
				def resChildren(inter: A): A = sn.children.foldLeft(inter)((acc, child) => fold(nextFirst)(acc, child)(op))
				def resNext(inter: A): A = sn.next.fold(inter)(next => fold(nextFirst)(inter, next)(op))
				if (nextFirst) resChildren(resNext(res))
				else resNext(resChildren(res))
		}
	}
}

class PageNode(val self: Node) extends ArchiveNode {
	def selfLabel = label.Page

	def location: AbsUri = Neo.txs { self[String]("location") }
	def pagetype: String = Neo.txs { self[String]("pagetype") }
	def pointerDescription: PointerDescription = Neo.txs { PointerDescription(location, pagetype) }

	//	def sha1 = Neo.txs { self.get[String]("sha1") }
	//	def sha1_=(sha: String) = Neo.txs { self.setProperty("sha1", sha) }

	def describes = Neo.txs { self.to { rel.describes }.map { StructureNode(_) } }

	def lastUpdate = Neo.txs { self.get[Long]("last_update").getOrElse(0L) }
	def lastUpdate_=(time: Long) = Neo.txs { self.setProperty("last_update", time) }

	override def toString = s"Page($location)"
}

object PageNode {
	def apply(node: Node) = new PageNode(node)
	def apply(nodeId: Long) = new PageNode(Neo.tx { _.getNodeById(nodeId) })

	def create(location: AbsUri, pagetype: String) = PageNode(Neo.create(label.Page, "location" -> location.toString, "pagetype" -> pagetype))
}

class StructureNode(val self: Node) extends ArchiveNode {
	def selfLabel = label.Structure

	def next = Neo.txs { self.to(rel.next).map { ArchiveNode(_) } }
	def children = Neo.txs { self.outgoing(rel.child).toIndexedSeq.sortBy(_[Int]("pos")).map { rel => ArchiveNode(rel.getStartNode) } }
	def payload: Option[ViscelNode] = Neo.txs { self.to(rel.payload).flatMap { ViscelNode(_).toOption } }

	override def toString = s"Structure($nid)"
}

object StructureNode {
	def apply(node: Node) = new StructureNode(node)
	def apply(nodeId: Long) = new StructureNode(Neo.tx { _.getNodeById(nodeId) })

	def create(): StructureNode = StructureNode(Neo.create(label.Structure))

	def create(payloadNodeOption: Option[ViscelNode], nextNodeOption: Option[ArchiveNode], childNodes: Seq[ArchiveNode]): StructureNode = Neo.txs {
		val newStructureNode = StructureNode.create()
		payloadNodeOption.foreach(payloadNode => newStructureNode.self.createRelationshipTo(payloadNode.self, rel.payload))
		nextNodeOption.foreach(nextNode => newStructureNode.self.createRelationshipTo(nextNode.self, rel.next))
		childNodes.zipWithIndex.foreach {
			case (childNode, index) =>
				val parentRelation = newStructureNode.self.createRelationshipTo(childNode.self, rel.child)
				parentRelation.setProperty("pos", index)
		}
		newStructureNode
	}
}
