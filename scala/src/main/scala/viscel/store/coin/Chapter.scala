package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.narration.Story
import viscel.store._
import viscel.database.{Ntx, NodeOps}


final case class Chapter(self: Node) extends StoryCoin with Metadata {

	def name(implicit neo: Ntx): String = self[String]("name")

	override def story(implicit neo: Ntx): Story.Chapter = Story.Chapter(name, metadata())

	def string(implicit neo: Ntx): String = s"Chapter(${ collection.name }, $name)"

	override def toString: String = s"Chapter($self)"
}

