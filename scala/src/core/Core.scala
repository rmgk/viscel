package viscel.core

import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri
import viscel._

class Core(val iopipe: spray.client.pipelining.SendReceive) extends Logging {

	val first = Uri("http://carciphona.com/view.php?page=cover&chapter=1&lang=")

	def go() = get(first).onComplete {
		case Success(cw) =>
			cw.elements.foreach(println)
		case Failure(e) =>
			println(s"failed to download ${e.getMessage}")
	}

	def get(uri: Uri) = {
		logger.info(s"get ${uri}")
		iopipe(Get(uri))
			.andThen { case Failure(e) => logger.warn(s"failed to download ${uri}: $e") }
			.map { res =>
				Jsoup.parse(res.entity.asString, uri.toString)
					.pipe { new CarciphonaWrapper(uri, _) }
			}
		// sprout.onSuccess{case sprout: core.Sprout => collection.store(sprout.seed.pos, sprout.elements.head)}

	}

	// def getElement(eseed: ElementSeed): Future[Element] = {
	// 	val inStore = Storage.find(eseed.source)
	// 	if (!inStore.isEmpty) {
	// 		logger.info(s"already has ${eseed.source}")
	// 		future { inStore.head }
	// 	}
	// 	else {
	// 		logger.info(s"get ${eseed.source}")
	// 		val resf = pipe(addHeader("referer", eseed.origin)(Get(eseed.source)))
	// 		resf.onFailure { case e => logger.error(s"failed to download ${eseed.source}: $e") }
	// 		val element = for {
	// 			res <- resf
	// 			spray.http.HttpBody(contentType, _) = res.entity
	// 			buffer = res.entity.buffer
	// 			sha1 = sha1hex(buffer)
	// 		} yield (contentType.value, buffer)
	// 		element.map {
	// 			case (ctype, buffer) =>
	// 				val sha1 = Storage.store(buffer)
	// 				val el = eseed(sha1, ctype)
	// 				Storage.put(el)
	// 				el
	// 		}
	// 	}
	// }

}
