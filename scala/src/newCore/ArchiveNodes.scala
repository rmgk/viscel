package viscel.newCore

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
import viscel.store._

trait ArchiveNode extends ViscelNode

object ArchiveNode {
	def apply(node: Node): ArchiveNode = Neo.txs { node.getLabels.toList } match {
		case List(l) if l == label.Page => PageNode(node)
		case List(l) if l == label.Structure => StructureNode(node)
	}
	def apply(id: Long): ArchiveNode = apply(Neo.tx { _.getNodeById(id) })
}

class PageNode(val self: Node) extends ArchiveNode {
	def selfLabel = label.Page

	def location: AbsUri = Neo.txs { self[String]("location") }
	def pagetype: String = Neo.txs { self[String]("pagetype") }

	def sha1 = Neo.txs { self.get[String]("sha1") }
	def sha1_=(sha: String) = Neo.txs { self.setProperty("sha1", sha) }

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
	def children = Neo.txs { self.incoming(rel.parent).toIndexedSeq.sortBy(_[Int]("pos")).map { rel => ArchiveNode(rel.getStartNode) } }
	def describes: Option[ViscelNode] = Neo.txs { self.to(rel.describes).flatMap { ViscelNode(_).toOption } }

	override def toString = s"Structure($nid)"
}

object StructureNode {
	def apply(node: Node) = new StructureNode(node)
	def apply(nodeId: Long) = new StructureNode(Neo.tx { _.getNodeById(nodeId) })

	def create() = StructureNode(Neo.create(label.Structure))
}
