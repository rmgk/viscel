package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.database.{NodeOps, Ntx}
import viscel.shared.Story
import viscel.store._


final case class Chapter(self: Node) extends AnyVal with StoryCoin with Metadata {

	def name(implicit neo: Ntx): String = self[String]("name")

	override def story(implicit neo: Ntx): Story.Chapter = Story.Chapter(name, metadata())

}

