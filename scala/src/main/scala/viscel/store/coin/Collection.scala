package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.store._
import viscel.time

final case class Collection(self: Node) extends Coin {

  def id: String = self[String]("id")
  def name: String = self.get[String]("name").getOrElse(id)
  def name_=(value: String) = self.setProperty("name", value)

  def first: Option[Asset] = self.to(rel.describes).flatMap(ArchiveNode(_).findForward { case an @ Asset(_) => an })
  def apply(n: Int): Option[Asset] = time(s"select $name($n)") {
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
