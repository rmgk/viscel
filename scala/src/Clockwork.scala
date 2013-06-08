package viscel

import akka.actor.{ActorSystem, Props, Actor}
import akka.io.IO
import spray.can.Http
import spray.client.pipelining._
import scala.concurrent._
import ExecutionContext.Implicits.global
import spray.client.pipelining._
import org.htmlcleaner._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import spray.http.Uri
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup

class Clockwork(val pipe: spray.client.SendReceive) extends Logging {

	val cE = new core.Experimental
	val collection = new viscel.collection.Memory("Carciphona")

	get(cE.first)

	def get(seed: core.Experimental#Seed) {
		logger.info(s"get ${seed.uri}")
		val resf = pipe(Get(seed.uri))
		resf.onFailure{case e => logger.error(s"failed to download ${seed.uri}: $e")}
		val sprout = for {
			res <- resf
			document = Jsoup.parse(res.entity.asString, seed.uri)
		} yield seed(document)

		sprout.onFailure{case e => logger.error(s"failed to download ${seed.uri}: $e")}

		sprout.onSuccess{case sprout: cE.Sprout => get(sprout.next)}
		sprout.onSuccess{case sprout: cE.Sprout => collection.store(sprout.seed.pos, sprout.elements.head)}

	}

}
