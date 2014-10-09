package viscel.store.nodes

import org.neo4j.graphdb.Node
import viscel.description._
import viscel.store._


final case class ChapterNode(self: Node) extends ArchiveNode with Metadata {

	def name: String = self[String]("name")

	override def description: Chapter = Chapter(name, metadata())

	override def toString = s"Chapter(${ collection.name }, $name)"
}

