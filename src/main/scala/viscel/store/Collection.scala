package viscel.store

import org.neo4j.graphdb.Node
import viscel.database.Traversal.findForward
import viscel.database.{NodeOps, Ntx, Traversal}

final case class Collection(self: Node) extends AnyVal {

	def id(implicit neo: Ntx): String = self[String]("id")
	def name(implicit neo: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)

	def first(implicit neo: Ntx): Option[Coin.Asset] = findForward(Coin.isAsset)(self)

	def size(implicit neo: Ntx): Int = Traversal.fold(0, self)(count => {
		case Coin.isAsset(a) => count + 1
		case _ => count
	})

}