package viscel.crawler

import org.neo4j.graphdb.Node
import org.scalactic.ErrorMessage
import viscel.crawler.Result.{Failed, Done, Continue, DelayedRequest}
import viscel.database.{NodeOps, Ntx, Util}
import viscel.narration.Narrator
import viscel.store.{Coin, Collection}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.Predef.any2ArrowAssoc
import scala.annotation.tailrec

trait Strategy {
	outer =>

	def run(implicit narrator: Narrator, ntx: Ntx): Result

	def andThen(afterwards: Strategy): Strategy = new Strategy {
		override def run(implicit narrator: Narrator, ntx: Ntx): Result = outer.run match {
			case Done(message) => afterwards.run
			case otherwise => otherwise
		}
	}

	def limit(count: Int): Strategy = new Strategy {
		override def run(implicit narrator: Narrator, ntx: Ntx): Result =
			if (count <= 0) Result.Done("limit reached")
			else outer.run.map(_.limit(count - 1))
	}
}

object Strategy {
	def const(result: Result): Strategy = new Strategy {
		override def run(implicit narrator: Narrator, ntx: Ntx): Result = result
	}

	lazy val mainStrategy: (Collection) => Strategy = collection =>
		walkStrategy(collection.self, ntx => n => n.next(ntx), unseenOnly(shallow = false))
			.andThen(fromEndStrategy(collection.self)(walkStrategy(_, ntx => n => n.prev(ntx), recheckOld).limit(4)))

	type Select = Ntx => Node => Option[Node]

	def explore(node: Node, narrator: Narrator, continue: Strategy, ntx: Ntx): Result = node match {
		case Coin.isPage(page) => IOUtil.documentRequest(page.location(ntx)) { IOUtil.writePage(narrator, page, continue) }
		case Coin.isAsset(asset) => IOUtil.blobRequest(asset.source(ntx), asset.origin(ntx)) { IOUtil.writeAsset(narrator, asset, continue) }
		case other => Result.Failed(s"can only request pages and assets not ${ other.getLabels.asScala.toList }" :: Nil)
	}

	@tailrec
	def repeat[R](n: Node, f: Node => Option[Node], s: Node => Option[R]): Option[R] = (s(n), f(n)) match {
		case (r@Some(_), _) => r
		case (None, Some(next)) => repeat(next, f, s)
		case (None, None) => None
	}

	val doneStrategy: Strategy = new Strategy {
		override def run(implicit narrator: Narrator, ntx: Ntx): Result = Result.Done("strategy is done")
	}

	def fromEndStrategy(node: Node)(f: Node => Strategy): Strategy = new Strategy {
		override def run(implicit narrator: Narrator, ntx: Ntx): Result = f(node.rightmost).run
	}

	def walkStrategy(start: Node, next: Ntx => Node => Option[Node], select: Select): Strategy = new Strategy {
		override def run(implicit narrator: Narrator, ntx: Ntx): Result =
			repeat(start, next(ntx), select(ntx)).fold[Result](Result.Done("walk complete"))(n => explore(n, narrator, walkStrategy(n, next, select), ntx))
	}

	def unseenOnly(shallow: Boolean): Select = ntx => {
		case n@Coin.isPage(page) if page.self.describes(ntx) eq null => Some(n)
		case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}

	def listStrategy(nodes: List[Node]): Strategy = new Strategy {
		override def run(implicit narrator: Narrator, ntx: Ntx): Result = nodes match {
			case h :: t => explore(h, narrator, listStrategy(t), ntx)
			case Nil => Result.Done("complete list checked")
		}
	}


	val recheckOld: Select = ntx => {
		case n@Coin.isPage(page) if Util.needsRecheck(n)(ntx) || (page.self.describes(ntx) eq null) => Some(n)
		case n@Coin.isAsset(asset) if asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}
}
