package viscel.crawler

import org.neo4j.graphdb.Node
import viscel.database.{Util, rel, Traversal, Ntx, NodeOps}
import viscel.store.{Collection, Coin}

trait Strategy {
	def run(implicit ntx: Ntx): Option[(Node, Strategy)]
	def andThen(other: Strategy): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = this.run match {
			case Some((node, next)) => Some(node -> next.andThen(other))
			case None => other.run
		}
	}
}

object Strategy {
	var mainStrategy: (Collection) => Strategy = col => unseenNext(shallow = false)(col.self).andThen(recheckOld(col))

	def forwardStrategy(start: Node)(select: Ntx => Node => Option[Node]): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] =
			Traversal.findForward(select(ntx))(start)(ntx).map(n => n -> forwardStrategy(n)(select))
	}

	def unseenNext(shallow: Boolean)(from: Node): Strategy = forwardStrategy(from)(ntx => {
		case n@Coin.isPage(page) if page.self.to(rel.describes)(ntx).isEmpty => Some(n)
		case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	})

	def recheckOld(collection: Collection): Strategy = forwardStrategy(collection.self)(ntx => {
		case n@Coin.isPage(page) if Util.needsRecheck(n)(ntx) || page.self.to(rel.describes)(ntx).isEmpty => Some(n)
		case n@Coin.isAsset(asset) if asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	})
}