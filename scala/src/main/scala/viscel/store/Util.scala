package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.tooling.GlobalGraphOperations
import org.scalactic.TypeCheckedTripleEquals._
import viscel.cores.Core
import viscel.store.nodes.CollectionNode
import viscel.time

import scala.Predef.any2ArrowAssoc
import scala.annotation.tailrec
import scala.collection.JavaConverters._

object Util extends StrictLogging {

	def listCollections: Vector[CollectionNode] = Neo.tx { db =>
		GlobalGraphOperations.at(db).getAllNodesWithLabel(label.Collection).asScala.map { CollectionNode.apply }.toVector
	}

	def listCores: Vector[Core] = Core.availableCores.toVector

	def search(query: String): Seq[Core] = time("search") {
		val lcql = Predef.wrapString(query.toLowerCase.replaceAll( """\s+""", "")).toList
		Neo.txs {
			if (lcql.isEmpty) listCores
			else
				listCores.view.map { cn => cn -> fuzzyMatch(lcql, Predef.wrapString(cn.name.toLowerCase).toList) }
					.filter { _._2 > 0 }.force
					.sortBy { -_._2 }
					.map { _._1 }
		}
	}

	@tailrec
	def fuzzyMatch(query: List[Char], text: List[Char], score: Long = 0, bestScore: Long = 0): Long = query match {
		case Nil => bestScore + score * score
		case q :: qs => text match {
			case Nil => 0
			case t :: ts =>
				if (t === q) fuzzyMatch(qs, ts, score + 1, bestScore)
				else fuzzyMatch(query, ts, 0, score * score + bestScore)
		}
	}

}
