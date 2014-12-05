package viscel.database

import org.neo4j.graphdb.Node
import viscel.Log

object Util {


	def updateDates(target: Node, changed: Boolean)(implicit ntx: Ntx): Unit = {
		val time = System.currentTimeMillis()
		val dayInMillis = 24L * 60L * 60L * 1000L
		val lastUpdateOption = target.get[Long]("last_update")
		val newCheckInterval: Long = (for {
			lastUpdate <- lastUpdateOption
			lastCheck <- target.get[Long]("last_check")
			checkInterval <- target.get[Long]("check_interval")
		} yield {
			math.round(checkInterval * (if (changed) 0.8 else 1.2))
		}).getOrElse(dayInMillis)
		val hasUpdated = changed || lastUpdateOption.isEmpty
		Log.trace(s"update dates on $target: time: $time, interval: $newCheckInterval has update: $hasUpdated")
		target.setProperty("last_check", time)
		if (hasUpdated) {
			target.setProperty("last_update", time)
		}
		target.setProperty("check_interval", newCheckInterval)
	}

	def needsRecheck(target: Node)(implicit ntx: Ntx): Boolean = {
		Log.trace(s"calculating recheck for $target")
		val res = for {
			lastCheck <- target.get[Long]("last_check")
			lastUpdate <- target.get[Long]("last_update")
			checkInterval <- target.get[Long]("check_interval")
		} yield {
			val time = System.currentTimeMillis()
			Log.trace(s"recheck $target: $time - $lastCheck > $checkInterval")
			time - lastCheck > checkInterval
		}
		res.getOrElse {
			Log.debug(s"defaulting to recheck because of missing data on $target")
			true
		}
	}

}
