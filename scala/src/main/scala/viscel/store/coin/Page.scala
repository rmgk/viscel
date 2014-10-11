package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.narration.Story
import viscel.store.StoryCoin
import viscel.database.{Ntx, NodeOps}


final case class Page(self: Node) extends StoryCoin {
	def location(implicit neo: Ntx): AbsUri = self[String]("location")
	def pagetype(implicit neo: Ntx): String = self[String]("pagetype")

	override def story(implicit neo: Ntx): Story.More = Story.More(location, pagetype)

	override def toString = s"Page($self)"
}
