package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.store.Coin
import viscel.database.{rel, Ntx, NodeOps, Traversal}
import viscel.database.Traversal.findForward
import viscel.time

import scala.annotation.tailrec

final case class Collection(self: Node) extends AnyVal with Coin {

	def id(implicit neo: Ntx): String = self[String]("id")
	def name(implicit neo: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)

	def first(implicit neo: Ntx): Option[Asset] = findForward(Coin.isAsset)(self)

	def apply(n: Int)(implicit neo: Ntx): Option[Asset] = time(s"select $name($n)") {
		@tailrec
		def nth(curr: Node, i: Int): Node =
			if (i > 0) curr.to(rel.skip) match {
				case None => curr
				case Some(node) => nth(node, i - 1)
			}
			else curr

		first.flatMap(node => Coin.isAsset(nth(node.self, n - 1)))
	}

	def size(implicit neo: Ntx): Int = first.fold(0)(_.distanceToLast)

	override def toString = s"Collection($self)"
}
