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
		walkStrategy(collection.self, ntx => n => n.next(ntx), unseenOnly(shallow = false))
			.andThen(fromEndStrategy(collection.self)(walkStrategy(_, ntx => n => n.prev(ntx), recheckOld).limit(4)))

	type Select = Ntx => Node => Option[Node]

	def repeat[R](n: Node, f: Node => Option[Node], s: Node => Option[R]): Option[R] = (s(n), f(n)) match {
		case (r @ Some(_), _) => r
		case (None, Some(next)) => repeat(next, f, s)
		case (None, None) => None
	}

	val noneStrategy: Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = None
	}

	def fromEndStrategy(node: Node)(f: Node => Strategy): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = f(node.rightmost).run
	}

	def walkStrategy(start: Node, next: Ntx => Node => Option[Node], select: Select): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] =
			repeat(start, next(ntx), select(ntx)).map(n => n -> walkStrategy(n, next, select))
	}

	def unseenOnly(shallow: Boolean): Select = ntx => {
		case n@Coin.isPage(page) if page.self.describes(ntx) eq null => Some(n)
		case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}

	def listStrategy(nodes: List[Node]): Strategy = new Strategy {
		override def run(implicit ntx: Ntx): Option[(Node, Strategy)] = nodes match {
			case h :: t => Some(h -> listStrategy(t))
			case Nil => None
		}
	}


	val recheckOld: Select = ntx => {
		case n@Coin.isPage(page) if Util.needsRecheck(n)(ntx) || (page.self.describes(ntx) eq null) => Some(n)
		case n@Coin.isAsset(asset) if asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}
}
