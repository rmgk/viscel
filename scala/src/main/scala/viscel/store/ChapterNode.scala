package viscel.store

import org.neo4j.graphdb.Node
import viscel.description._

import scala.collection.JavaConverters._

class ChapterNode(val self: Node) extends ArchiveNode with Metadata {

	def selfLabel = label.Chapter

	def name: String = Neo.txs { self[String]("name") }

	override def description: Chapter = Neo.txs {
		Chapter(name, metadata)
	}

	override def toString = s"$selfLabel(${ collection.name }, $name)"
}

object ChapterNode {
	def apply(node: Node) = new ChapterNode(node)

	def create(desc: Chapter): ChapterNode = ChapterNode(Neo.create(label.Chapter, Metadata.prefix(desc.metadata) + ("name" -> desc.name)))
}
