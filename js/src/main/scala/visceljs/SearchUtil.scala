package visceljs


import scala.Predef.any2ArrowAssoc
import scala.annotation.tailrec

object SearchUtil {
	def search[T](query: String, items: List[(String, T)]): Seq[T] = {
		val lcql = query.toLowerCase.replaceAll( """\s+""", "").toList
		if (lcql.isEmpty) Nil
		else
			items.view.map { item => item -> fuzzyMatch(lcql, item._1.toLowerCase.toList) }
				.filter { _._2 > 0 }.force
				.sortBy { -_._2 }
				.map { _._1._2 }
	}

	@tailrec
	def fuzzyMatch(query: List[Char], text: List[Char], score: Long = 0, bestScore: Long = 0): Long = query match {
		case Nil => bestScore + score * score
		case q :: qs => text match {
			case Nil => 0
			case t :: ts =>
				if (t == q) fuzzyMatch(qs, ts, score + 1, bestScore)
				else fuzzyMatch(query, ts, 0, score * score + bestScore)
		}
	}
}
