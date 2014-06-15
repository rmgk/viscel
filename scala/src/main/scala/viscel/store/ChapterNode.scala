package viscel.store

import org.neo4j.graphdb.Node

class ChapterNode(val self: Node) extends ViscelNode {

	def selfLabel = label.Chapter

	def name: String = Neo.txs { self[String]("name") }

	def collection = Neo.txs { self.to(rel.parent).map { CollectionNode(_) }.get }
	def collection_=(cn: CollectionNode) = Neo.txs { self.to_=(rel.parent, cn.self) }

	def apply(k: String) = Neo.txs { self[String](k) }
	def get(k: String) = Neo.txs { self.get[String](k) }

	override def toString = s"$selfLabel(${ collection.name }, $name)"
}

object ChapterNode {
	def apply(node: Node) = new ChapterNode(node)
	def apply(id: String) = Neo.node(label.Chapter, "id", id).map { new ChapterNode(_) }

	def create(name: String, props: (String, Any)*) = ChapterNode(Neo.create(label.Chapter, ("name" -> name) +: props: _*))
}
