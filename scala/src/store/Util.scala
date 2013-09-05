package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.tooling.GlobalGraphOperations
import scala.collection.JavaConversions._
import util.Try
import viscel.time

object Util {
	def list = Neo.tx { db => GlobalGraphOperations.at(db).getAllNodesWithLabel(label.Collection).toStream.map { CollectionNode(_) } }

	def search(query: String) = time("search") {
		val lcql = query.toLowerCase.replaceAll("""\s+""", "").toList
		Neo.txs {
			list.map { cn => cn -> fuzzyMatch(lcql, cn.name.toLowerCase.toList) }
				.filter { _._2 > 0 }
				.sortBy { -_._2 }
				.map { _._1 }
				.toIndexedSeq
		}
	}

	def fuzzyMatch(query: List[Char], text: List[Char], score: Long = 0, bestScore: Long = 0, block: Boolean = false): Long = query match {
		case Nil => bestScore + score * score
		case q :: qs => text match {
			case Nil => 0
			case t :: ts =>
				if (t == q) fuzzyMatch(qs, ts, score + 1, bestScore, true)
				else fuzzyMatch(query, ts, 0, score * score + bestScore, false)
		}
	}

}
