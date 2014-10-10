package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.narration.Story
import viscel.store.StoryCoin
import viscel.store.archive.NodeOps



final case class Page(self: Node) extends StoryCoin {
  def location: AbsUri = self[String]("location")
  def pagetype: String = self[String]("pagetype")

  override def story: Story.More = Story.More(location, pagetype)

  override def toString = s"Page($location)"
}
