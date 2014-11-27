package viscel.crawler

import org.neo4j.graphdb.Node
import viscel.database.{Util, rel, Ntx, NodeOps}
import viscel.store.{Collection, Coin}
import scala.Predef.any2ArrowAssoc

trait Strategy {
	outer =>
	def run(implicit ntx: Ntx): Option[(Node, Strategy)]
	def andThen(other: Strategy): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = outer.run match {
			case Some((node, next)) => Some(node -> next.andThen(other))
			case None => other.run
		}
	}
	def limit(count: Int): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] =
			if (count <= 0) None
			else outer.run.map{case (node, next) => node -> next.limit(count - 1)}
	}
}

object Strategy {
	lazy val mainStrategy: (Collection) => Strategy = collection =>
		forwardStrategy(collection.self)(unseenOnly(shallow = false))
			.andThen(fromEndStrategy(collection.self)(backwardStrategy(_)(recheckOld).limit(4)))

	type Select = Ntx => Node => Option[Node]

	val noneStrategy: Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = None
	}

	def fromEndStrategy(node: Node)(f: Node => Strategy): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = f(node.rightmost).run
	}

	def forwardStrategy(start: Node)(select: Select): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] =
			Predef.??? //Traversal.findForward(select(ntx))(start)(ntx).map(n => n -> forwardStrategy(n)(select))
	}

	def backwardStrategy(start: Node)(select: Select): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] =
			Predef.??? //Traversal.findBackward(select(ntx))(start)(ntx).map(n => n -> backwardStrategy(n)(select))
	}

	def unseenOnly(shallow: Boolean): Select = ntx => {
		Predef.??? //case n@Coin.isPage(page) if page.self.to(rel.describes)(ntx).isEmpty => Some(n)
		//case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
		//case _ => None
	}

	def listStrategy(nodes: List[Node]): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = nodes match {
			case h :: t => Some(h -> listStrategy(t))
			case Nil => None
		}
	}
	
	def rightLeanedStrategy(start: Node)(select: Select): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = {
			Predef.??? //val (notPages, pageAndRest) = Traversal.layerBelow(start).reverse.span(Coin.isPage.andThen(_.isEmpty))
			//val check = (pageAndRest.take(1) ::: notPages) filter select(ntx).andThen(_.isDefined)
			//val strat = listStrategy(check) andThen pageAndRest.headOption.fold(noneStrategy)(rightLeanedStrategy(_)(select))
			//strat.run
		}
	}

	val recheckOld: Select = ntx => {
		Predef.??? //case n@Coin.isPage(page) if Util.needsRecheck(n)(ntx) || page.self.to(rel.describes)(ntx).isEmpty => Some(n)
		//case n@Coin.isAsset(asset) if asset.blob(ntx).isEmpty => Some(n)
		//case _ => None
	}
}
