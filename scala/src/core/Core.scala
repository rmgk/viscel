package viscel.core

import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import viscel.store._

class Runner(definition: Definition) {
	val collection = CollectionNode(definition.id).get
}

class Core(val iopipe: SendReceive) extends Logging {

	val definitions = Seq(CarciphonaWrapper)

	def document(uri: Uri) = iopipe(Get(uri)).map { res => Jsoup.parse(res.entity.asString, uri.toString) }

	def wrap(loc: Uri, wrapper: Wrapper) = loc.pipe { document }.map { wrapper }

	def test() = {
		val cp = definitions.head
		wrapNext(cp.first, cp.wrapper)
	}

	def wrapNext(loc: Uri, wrapper: Wrapper): Unit = wrap(loc, wrapper).onComplete {
		case Failure(e) => logger.warn(s"failed download")
		case Success(wrapped) =>
			wrapped.next.foreach { n =>
				logger.info(s"next is $n")
				wrapNext(n, wrapper)
			}
			wrapped.next.recover { case t => t.getMessage.pipe { println } }
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
