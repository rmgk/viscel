package viscel

import akka.actor.{ActorSystem, Props, Actor}
import akka.io.IO
import spray.can.Http
import spray.client.pipelining._
import scala.concurrent._
import ExecutionContext.Implicits.global
import org.htmlcleaner._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import spray.http.Uri
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup

class Clockwork(val pipe: spray.client.pipelining.SendReceive) extends Logging {

	val cE = new core.Carciphona
	val collection = new viscel.collection.Memory("Carciphona")

	get(cE.first)

	def get(seed: core.Seed) {
		logger.info(s"get ${seed.uri}")
		val resf = pipe(Get(seed.uri))
		resf.onFailure{case e => logger.error(s"failed to download ${seed.uri}: $e")}
		val sprout = for {
			res <- resf
			document = Jsoup.parse(res.entity.asString, seed.uri)
		} yield seed(document)

		sprout.onFailure{case e => logger.error(s"failed to download ${seed.uri}: $e")}

		sprout.foreach{ sprout =>
			sprout.elements.foreach{ getElement(_).foreach{el => collection.store(sprout.seed.pos, el)} }

			get(sprout.next)
		}
		// sprout.onSuccess{case sprout: core.Sprout => collection.store(sprout.seed.pos, sprout.elements.head)}

	}

	def getElement(eseed: ElementSeed): Future[Element] = ??? //{
	// 	val inStore = Storage.find(eseed.source)
	// 	if (!inStore.isEmpty) {
	// 		logger.info(s"already has ${eseed.source}")
	// 		future { inStore.head }
	// 	}
	// 	else {
	// 		logger.info(s"get ${eseed.source}")
	// 		val resf = pipe(addHeader("referer", eseed.origin)(Get(eseed.source)))
	// 		resf.onFailure{case e => logger.error(s"failed to download ${eseed.source}: $e")}
	// 		val element = for {
	// 			res <- resf
	// 			spray.http.HttpBody(contentType, _) = res.entity
	// 			buffer = res.entity.buffer
	// 			sha1 = sha1hex(buffer)
	// 		} yield (contentType.value, buffer)
	// 		element.map{ case (ctype, buffer) =>
	// 			val sha1 = Storage.store(buffer)
	// 			val el = eseed(sha1, ctype)
	// 			Storage.put(el)
	// 			el
	// 		}
	// 	}
	// }

}
