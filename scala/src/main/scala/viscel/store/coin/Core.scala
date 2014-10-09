package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.description.Story
import viscel.store.{Metadata, ArchiveNode, NodeOps}

import scala.language.implicitConversions

final case class Core(self: Node) extends ArchiveNode with Metadata {

	def kind: String = self[String]("kind")
	def id: String = self[String]("id")
	def name: String = self[String]("name")

	override def story: Story.Core = Story.Core(kind, id, name, metadata())
}

