package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.tooling.GlobalGraphOperations
import org.scalactic.TypeCheckedTripleEquals._
import viscel.time

import scala.annotation.tailrec
import scala.collection.JavaConversions._

object Util extends StrictLogging {

	def purgeUnreferenced() = {
		//		val collections = Neo.txs {
		//			list.filter { col =>
		//				!col.self.outgoing(rel.bookmark).exists { r =>
		//					!Option(r.getEndNode).flatMap { _.from(rel.bookmarked) }.isEmpty
		//				}
		//			}.toIndexedSeq
		//		}
		//		collections.foreach { col =>
		//			Neo.txs {
		//				logger.info(s"deleting ${col.name}")
		//				col.children.foreach { ch =>
		//					ch.children.foreach { el =>
		//						el.deleteNode(warn = false)
		//					}
		//					ch.deleteNode()
		//				}
		//				col.deleteNode()
		//			}
		//		}
	}

	def list: IndexedSeq[CollectionNode] = Neo.tx { db => GlobalGraphOperations.at(db).getAllNodesWithLabel(label.Collection).map { CollectionNode(_) }.toIndexedSeq }

	def search(query: String): IndexedSeq[CollectionNode] = time("search") {
		val lcql = query.toLowerCase.replaceAll( """\s+""", "").toList
		Neo.txs {
			if (lcql.isEmpty) list
			else
				list.view.map { cn => cn -> fuzzyMatch(lcql, cn.name.toLowerCase.toList) }
					.filter { _._2 > 0 }
					.sortBy { -_._2 }
					.map { _._1 }
					.toIndexedSeq
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