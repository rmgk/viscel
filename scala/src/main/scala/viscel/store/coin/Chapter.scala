package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.narration.Story
import viscel.store._
import viscel.database.NodeOps


final case class Chapter(self: Node) extends StoryCoin with Metadata {

	def name: String = self[String]("name")

	override def story: Story.Chapter = Story.Chapter(name, metadata())

	override def toString = s"Chapter(${ collection.name }, $name)"
}

