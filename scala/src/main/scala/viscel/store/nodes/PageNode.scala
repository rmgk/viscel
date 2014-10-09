package viscel.store.nodes

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.description.Pointer
import viscel.store.{ArchiveNode, DescribingNode, Neo, NodeOps}


final case class PageNode(self: Node) extends ArchiveNode with DescribingNode {
  def location: AbsUri = self.apply[String]("location")
  def pagetype: String = self[String]("pagetype")

  override def deleteRecursive()(implicit neo: Neo): Unit = {
    describes.foreach { _.layer.foreach(_.deleteRecursive()) }
    Neo.delete(self)
  }

  override def description: Pointer = Pointer(location, pagetype)

  override def toString = s"Page($location)"
}



