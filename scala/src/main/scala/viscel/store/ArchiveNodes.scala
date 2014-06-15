package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.core.{AbsUri, PointerDescription}

import scala.annotation.tailrec
import scala.collection.JavaConversions._

sealed trait ArchiveNode extends ViscelNode {
	def flatten: List[ArchiveNode] = ArchiveNode.flatten(List[ArchiveNode](), List(this))
	def flatPayload: List[ViscelNode] = flatten.collect { case sn: StructureNode => sn.payload }.flatten.reverse
}

object ArchiveNode extends StrictLogging {
	def apply(node: Node): ArchiveNode = Neo.txs { node.getLabels.toList } match {
		case List(l) if l === label.Page => PageNode(node)
		case List(l) if l === label.Structure => StructureNode(node)
	}
	def apply(id: Long): ArchiveNode = apply(Neo.tx { _.getNodeById(id) })
	def apply(cn: CollectionNode): Option[ArchiveNode] = Neo.txs { cn.self.to(rel.archive).map { apply } }

	@tailrec
	def flatten(acc: List[ArchiveNode], node: List[ArchiveNode], shallow: Boolean = false): List[ArchiveNode] = node match {
		case Nil => acc
		case (pn: PageNode) :: rest => flatten(pn :: acc, if (shallow) rest else pn.describes.fold(rest)(rest.::), shallow)
		case (sn: StructureNode) :: rest =>
			flatten(sn :: acc, sn.children.toList ::: sn.next.fold(rest)(rest.::), shallow)
	}

}

class PageNode(val self: Node) extends ArchiveNode {
	def selfLabel = label.Page

	def location: AbsUri = Neo.txs { self[String]("location") }
	def pagetype: String = Neo.txs { self[String]("pagetype") }
	def pointerDescription: PointerDescription = Neo.txs { PointerDescription(location, pagetype) }

	def describes = Neo.txs { self.to { rel.describes }.map { StructureNode(_) } }
	def describes_=(other: ArchiveNode): Unit = Neo.txs { self.createRelationshipTo(other.self, rel.describes) }

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

	def next: Option[ArchiveNode] = Neo.txs { self.to(rel.next).map { ArchiveNode(_) } }
	def children: Vector[ArchiveNode] = Neo.txs { self.outgoing(rel.child).toVector.sortBy(_[Int]("pos")).map { rel => ArchiveNode(rel.getEndNode) } }
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
