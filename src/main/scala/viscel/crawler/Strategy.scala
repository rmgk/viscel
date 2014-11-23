package viscel.crawler

import org.neo4j.graphdb.Node
import viscel.database.{Util, rel, Traversal, Ntx, NodeOps}
import viscel.store.{Collection, Coin}
import scala.Predef.any2ArrowAssoc

trait Strategy {
	def run(implicit ntx: Ntx): Option[(Node, Strategy)]
	def andThen(other: Strategy): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = Strategy.this.run match {
			case Some((node, next)) => Some(node -> next.andThen(other))
			case None => other.run
		}
	}
}

object Strategy {
	lazy val mainStrategy: (Collection) => Strategy = collection =>
		forwardStrategy(collection.self)(unseenOnly(shallow = false))

	type Select = Ntx => Node => Option[Node]

	val noneStrategy: Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = None
	}

	def forwardStrategy(start: Node)(select: Select): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] =
			Traversal.findForward(select(ntx))(start)(ntx).map(n => n -> forwardStrategy(n)(select))
	}

	def unseenOnly(shallow: Boolean): Select = ntx => {
		case n@Coin.isPage(page) if page.self.to(rel.describes)(ntx).isEmpty => Some(n)
		case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}

	def listStrategy(nodes: List[Node]): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = nodes match {
			case h :: t => Some(h -> listStrategy(t))
			case Nil => None
		}
	}
	
	def rightLeanedStrategy(start: Node)(select: Select): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = {
			val (notPages, pageAndRest) = Traversal.layerBelow(start).reverse.span(Coin.isPage.andThen(_.isEmpty))
			val check = (pageAndRest.take(1) ::: notPages) filter select(ntx).andThen(_.isDefined)
			val strat = listStrategy(check) andThen pageAndRest.headOption.fold(noneStrategy)(rightLeanedStrategy(_)(select))
			strat.run
		}
	}

	val recheckOld: Select = ntx => {
		case n@Coin.isPage(page) if Util.needsRecheck(n)(ntx) || page.self.to(rel.describes)(ntx).isEmpty => Some(n)
		case n@Coin.isAsset(asset) if asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}
}
