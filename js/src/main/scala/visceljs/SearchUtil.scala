package visceljs

import scala.annotation.tailrec
import scala.util.control.NonFatal

object SearchUtil {
  def search[T](query: String, items: List[(String, T)]): List[T] = {
    val lcql = normstr(query.toLowerCase.replaceAll("""\s+""", ""))
    if (lcql.isEmpty) items.map(_._2)
    else
      items
        .view
        .map { item => item -> fuzzyMatch(lcql, normstr(item._1.toLowerCase)) }
        .filter { _._2 > 0 }
        .toList
        .sortBy { -_._2 }
        .map { _._1._2 }
  }

  def normstr(in: String) =
    try {
      in.asInstanceOf[scalajs.js.Dynamic].normalize("NFD").asInstanceOf[String]
    } catch {
      case NonFatal(e) => in
    }

  def fuzzyMatch(query: String, text: String): Long = {
    @tailrec
    def rec(score: Long, bestScore: Long, qpos: Int, tpos: Int): Long = {
      if (query.length <= qpos) {
        bestScore + score * score
      } else {
        if (text.length <= tpos) {
          0
        } else {
          if (text(tpos) == query(qpos)) rec(score + 1, bestScore, qpos + 1, tpos + 1)
          else rec(0, score * score + bestScore, qpos, tpos + 1)
        }
      }
    }
    rec(0, 0, 0, 0)
  }
}
