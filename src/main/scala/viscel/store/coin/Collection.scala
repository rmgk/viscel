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

	def size(implicit neo: Ntx): Int = Traversal.fold(0, self)(count => {
		case Coin.isAsset(a) => count + 1
		case _ => count
	})

}
