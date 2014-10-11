package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.store.Coin
import viscel.database.{NodeOps, Traversal}
import viscel.database.Traversal.findForward
import viscel.time

final case class Collection(self: Node) extends Coin {

	def id: String = self[String]("id")
	def name: String = self.get[String]("name").getOrElse(id)
	def name_=(value: String) = self.setProperty("name", value)

	def first: Option[Asset] = findForward(Coin.isAsset)(self)

	def apply(n: Int): Option[Asset] = time(s"select $name($n)") {
		var i = 1
		var res = first
		while (i < n) {
			res = res.flatMap(_.next)
			i += 1
		}
		res
	}

	override def toString = s"Collection($id)"
}
