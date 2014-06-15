package viscel.store

import org.neo4j.graphdb.Node
import viscel.time

class CollectionNode(val self: Node) extends ViscelNode {
	def selfLabel = label.Collection

	def id: String = Neo.txs { self[String]("id") }
	def name: String = Neo.txs { self.get[String]("name").getOrElse(id) }
	def name_=(value: String) = Neo.txs { self.setProperty("name", value) }

	def archive: Option[ArchiveNode] = Neo.txs { self.to(rel.archive).map { ArchiveNode(_) } }

	//	def totalSize = Neo.txs { children.map { _.size }.sum }

	def lastUpdate = Neo.txs { self.get[Long]("last_update").getOrElse(0L) }
	def lastUpdate_=(time: Long) = Neo.txs { self.setProperty("last_update", time) }

	def last: Option[ElementNode] = Neo.txs { self.to(rel.last).map { ElementNode(_) } }
	def last_=(en: ElementNode) = Neo.txs { self.to_=(rel.last, en.self) }
	def first: Option[ElementNode] = Neo.txs { self.to(rel.first).map { ElementNode(_) } }
	def first_=(en: ElementNode) = Neo.txs { self.to_=(rel.first, en.self) }
	def size: Int = Neo.txs { last.fold(0){ _.position } }
	def apply(n: Int): Option[ElementNode] = time(s"select $name($n)") {
		var i = 1
		var res = first
		while (i < n) {
			res = res.flatMap(_.next)
			i += 1
		}
		res
	}

	override def toString = s"Collection($id)"
}

object CollectionNode {
	def apply(node: Node) = new CollectionNode(node)
	def apply(nodeId: Long) = new CollectionNode(Neo.tx { _.getNodeById(nodeId) })
	def apply(id: String) = Neo.node(label.Collection, "id", id).map { new CollectionNode(_) }

	def create(id: String, name: String) = CollectionNode(Neo.create(label.Collection, "id" -> id, "name" -> name))
}
