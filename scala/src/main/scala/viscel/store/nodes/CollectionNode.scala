package viscel.store.nodes

import org.neo4j.graphdb.Node
import viscel.store._
import viscel.time

final case class CollectionNode(self: Node) extends ViscelNode with DescribingNode {

  def id: String = self[String]("id")
  def name: String = self.get[String]("name").getOrElse(id)
  def name_=(value: String) = self.setProperty("name", value)

  def first: Option[AssetNode] = describes.flatMap(_.findForward { case an: AssetNode => an })
  def apply(n: Int): Option[AssetNode] = time(s"select $name($n)") {
    var i = 1
    var res = first
    while (i < n) {
      res = res.flatMap(_.nextAsset)
      i += 1
    }
    res
  }

  override def toString = s"Collection($id)"
}
