package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.description.Story
import viscel.store.{ArchiveNode, Neo, NodeOps}


final case class Page(self: Node) extends ArchiveNode {
  def location: AbsUri = self[String]("location")
  def pagetype: String = self[String]("pagetype")

  override def story: Story.More = Story.More(location, pagetype)

  override def toString = s"Page($location)"
}
