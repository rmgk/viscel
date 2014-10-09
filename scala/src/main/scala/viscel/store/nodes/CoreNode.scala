package viscel.store.nodes

import org.neo4j.graphdb.Node
import viscel.description.Description._
import viscel.store.{Metadata, ArchiveNode, NodeOps}

import scala.language.implicitConversions

final case class CoreNode(self: Node) extends ArchiveNode with Metadata {

	def kind: String = self[String]("kind")
	def id: String = self[String]("id")
	def name: String = self[String]("name")

	override def description: CoreDescription = CoreDescription(kind, id, name, metadata())
}

