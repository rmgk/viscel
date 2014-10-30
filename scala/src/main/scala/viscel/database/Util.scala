package viscel.database

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import org.neo4j.tooling.GlobalGraphOperations
import org.scalactic.TypeCheckedTripleEquals._
import viscel.narration.Narrator
import viscel.store.coin.Collection
import viscel.time

import scala.Predef.any2ArrowAssoc
import scala.annotation.tailrec
import scala.collection.JavaConverters._

object Util extends StrictLogging {

	def listCollections(implicit neo: Ntx): Vector[Collection] =
		GlobalGraphOperations.at(neo.db).getAllNodesWithLabel(label.Collection).asScala.map { Collection.apply }.toVector

	def listCores: Vector[Narrator] = Narrator.availableCores.toVector

	def search(query: String): Seq[Narrator] = time("search") {
		val lcql = Predef.wrapString(query.toLowerCase.replaceAll( """\s+""", "")).toList
		if (lcql.isEmpty) listCores
		else
			listCores.view.map { cn => cn -> fuzzyMatch(lcql, Predef.wrapString(cn.name.toLowerCase).toList) }
				.filter { _._2 > 0 }.force
				.sortBy { -_._2 }
				.map { _._1 }
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


	def updateDates(target: Node, changed: Boolean)(implicit ntx: Ntx): Unit = {
		val time = System.currentTimeMillis()
		val dayInMillis = 24L * 60L * 60L * 1000L
		val newCheckInterval: Long = (for {
			lastCheck <- target.get[Long]("last_check")
			lastUpdate <- target.get[Long]("last_update")
			checkInterval <- target.get[Long]("check_interval")
		} yield {
			val lowerBound = lastCheck - lastUpdate
			val upperBound = time - lastUpdate
			val averageBound = (lowerBound + upperBound) / 2
			math.round(checkInterval * 0.8 + averageBound * 0.2)
		}).getOrElse(dayInMillis)
		logger.trace(s"update dates on $target: time: $time, interval: $newCheckInterval")
		target.setProperty("last_check", time)
		if (changed) target.setProperty("last_update", time)
		target.setProperty("check_interval", newCheckInterval)
	}

	def needsRecheck(target: Node)(implicit ntx: Ntx): Boolean = {
		logger.trace(s"calculating recheck for $target")
		val res = for {
			lastCheck <- target.get[Long]("last_check")
			lastUpdate <- target.get[Long]("last_update")
			checkInterval <- target.get[Long]("check_interval")
		} yield {
			val time = System.currentTimeMillis()
			logger.trace(s"recheck $target: $time - $lastCheck > $checkInterval")
			time - lastCheck > checkInterval
		}
		res.getOrElse {
			logger.debug(s"defaulting to recheck because of missing data on $target")
			true
		}
	}

}
