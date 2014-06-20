package viscel.store

import org.neo4j.graphdb.Node
import viscel.description._

import scala.collection.JavaConverters._

class ChapterNode(val self: Node) extends ArchiveNode {

	def selfLabel = label.Chapter

	def name: String = Neo.txs { self[String]("name") }

	def apply(k: String) = Neo.txs { self[String](k) }
	def get(k: String) = Neo.txs { self.get[String](k) }

	override def description: Chapter = Neo.txs {
		val props = self.getPropertyKeys.asScala.map(key => key -> self[String](key)).toMap
		Chapter(props("name"), props - "name")
	}

	override def toString = s"$selfLabel(${ collection.name }, $name)"
}

object ChapterNode {
	def apply(node: Node) = new ChapterNode(node)

	def create(name: String, props: Map[String, String]): ChapterNode = ChapterNode(Neo.create(label.Chapter, props + ("name" -> name)))
	def create(name: String, props: (String, String)*): ChapterNode = create(name, props.toMap)
}
