package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.narration.Story
import viscel.database.{Ntx, NodeOps}
import viscel.store.{Metadata, StoryCoin}

import scala.language.implicitConversions

final case class Core(self: Node) extends StoryCoin with Metadata {

	def kind(implicit neo: Ntx): String = self[String]("kind")
	def id(implicit neo: Ntx): String = self[String]("id")
	def name(implicit neo: Ntx): String = self[String]("name")

	override def story(implicit neo: Ntx): Story.Core = Story.Core(kind, id, name, metadata())
}

