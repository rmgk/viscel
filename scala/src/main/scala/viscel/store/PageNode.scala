package viscel.store

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.description.Pointer

import scala.Predef.any2ArrowAssoc

object PageNode {
	def apply(node: Node) = new PageNode(node)
	def apply(nodeId: Long) = new PageNode(Neo.tx { _.getNodeById(nodeId) })

	def create(location: AbsUri, pagetype: String) = PageNode(Neo.create(label.Page, "location" -> location.toString, "pagetype" -> pagetype))

	def unapply(pn: PageNode): Some[PageNode] = Some(pn)
}

class PageNode(val self: Node) extends ArchiveNode with DescribingNode {
	def selfLabel = label.Page

	def location: AbsUri = Neo.txs { self[String]("location") }
	def pagetype: String = Neo.txs { self[String]("pagetype") }

	def lastUpdate = Neo.txs { self.get[Long]("last_update").getOrElse(0L) }
	def lastUpdate_=(time: Long) = Neo.txs { self.setProperty("last_update", time) }

	override def deleteRecursive(): Unit = Neo.txs {
		describes.foreach { _.layer.foreach(_.deleteRecursive()) }
		deleteNode(warn = false)
	}

	override def description: Pointer = Neo.txs { Pointer(location, pagetype) }

	override def toString = s"Page($location)"
}



