package viscel.store

import org.neo4j.graphdb.Node
import viscel.time
import viscel.cores.Core

import scala.Predef.any2ArrowAssoc

class CollectionNode(val self: Node) extends ViscelNode with DescribingNode {
	def selfLabel = label.Collection

	def id: String = Neo.txs { self[String]("id") }
	def name: String = Neo.txs { self.get[String]("name").getOrElse(id) }
	def name_=(value: String) = Neo.txs { self.setProperty("name", value) }

	//def archive: Option[ArchiveNode] = Neo.txs { self.to(rel.archive).map { ArchiveNode(_) } }

	//	def totalSize = Neo.txs { children.map { _.size }.sum }

	def lastUpdate = Neo.txs { self.get[Long]("last_update").getOrElse(0L) }
	def lastUpdate_=(time: Long) = Neo.txs { self.setProperty("last_update", time) }

	def first: Option[AssetNode] = Neo.txs { describes.flatMap(_.findForward { case an: AssetNode => an }) }
	def apply(n: Int): Option[AssetNode] = time(s"select $name($n)") {
		var i = 1
		var res = first
		while (i < n) {
			res = res.flatMap(_.nextAsset)
			i += 1
		}
		res
	}

	override def toString = s"Collection($id)"
}

object CollectionNode {
	def apply(node: Node) = new CollectionNode(node)
	def apply(nodeId: Long) = new CollectionNode(Neo.tx { _.getNodeById(nodeId) })
	def apply(id: String): Option[CollectionNode] = Neo.node(label.Collection, "id", id).map { new CollectionNode(_) }

	def create(id: String, name: String) = CollectionNode(Neo.create(label.Collection, "id" -> id, "name" -> name))

	def getOrCreate(core: Core): CollectionNode = Neo.txs {
		apply(core.id).getOrElse { create(core.id, core.name) }
	}
}
