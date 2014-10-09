package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.description.Story
import viscel.store._


final case class Chapter(self: Node) extends ArchiveNode with Metadata {

	def name: String = self[String]("name")

	override def story: Story.Chapter = Story.Chapter(name, metadata())

	override def toString = s"Chapter(${ collection.name }, $name)"
}

