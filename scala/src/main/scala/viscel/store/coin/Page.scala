package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.description.Story
import viscel.store.{ArchiveNode, DescribingNode, Neo, NodeOps}


final case class Page(self: Node) extends ArchiveNode with DescribingNode {
  def location: AbsUri = self.apply[String]("location")
  def pagetype: String = self[String]("pagetype")

  override def deleteRecursive()(implicit neo: Neo): Unit = {
    describes.foreach { _.layer.foreach(_.deleteRecursive()) }
    Neo.delete(self)
  }

  override def story: Story.More = Story.More(location, pagetype)

  override def toString = s"Page($location)"
}
