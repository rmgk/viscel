package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.database.{NodeOps, Ntx}
import viscel.shared.{AbsUri, Story}
import viscel.store.StoryCoin


final case class Page(self: Node) extends AnyVal with StoryCoin {
	def location(implicit neo: Ntx): AbsUri = self[String]("location")
	def pagetype(implicit neo: Ntx): String = self[String]("pagetype")

	override def story(implicit neo: Ntx): Story.More = Story.More(location, pagetype)
}
