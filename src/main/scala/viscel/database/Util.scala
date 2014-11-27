package viscel.database

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node

object Util extends StrictLogging {


	def updateDates(target: Node, changed: Boolean)(implicit ntx: Ntx): Unit = {
		val time = System.currentTimeMillis()
		val dayInMillis = 24L * 60L * 60L * 1000L
		val lastUpdateOption = target.get[Long]("last_update")
		val newCheckInterval: Long = (for {
			lastUpdate <- lastUpdateOption
			lastCheck <- target.get[Long]("last_check")
			checkInterval <- target.get[Long]("check_interval")
		} yield {
			val lowerBound = lastCheck - lastUpdate
			val upperBound = time - lastUpdate
			val averageBound = (lowerBound + upperBound) / 2
			math.round(checkInterval * 0.8 + averageBound * 0.2)
		}).getOrElse(dayInMillis)
		val hasUpdated = changed || lastUpdateOption.isEmpty
		logger.trace(s"update dates on $target: time: $time, interval: $newCheckInterval has update: $hasUpdated")
		target.setProperty("last_check", time)
		if (hasUpdated) {
			target.setProperty("last_update", time)
		}
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
