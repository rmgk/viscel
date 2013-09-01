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
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ContentType
import scalax.io._
import scala.collection.JavaConversions._
import org.neo4j.graphdb.Direction

trait NetworkPrimitives extends Logging {

	def iopipe: SendReceive

	def response(uri: Uri, referer: Option[Uri] = None): Future[HttpResponse] = {
		logger.info(s"get $uri ($referer)")
		val addReferer = referer match {
			case Some(ref) => addHeader("referer", ref.toString)
			case None => (x: HttpRequest) => x
		}
		Get(uri).pipe { addReferer }.pipe { iopipe }
			.flatMap { res => res.validate(_.status.intValue == 200, endRun(s"invalid response ${res.status}; $uri ($referer)")).toFuture }
	}

	def document(uri: Uri): Future[Document] = response(uri).map { res =>
		Jsoup.parse(
			res.entity.asString,
			res.header[Location].map { _.uri }.getOrElse(uri).toString)
	}

	def elementData(eseed: Element): Future[ElementData] = {
		response(eseed.source, Some(eseed.origin)).map { res =>
			ElementData(
				mediatype = res.header[`Content-Type`].get.contentType,
				buffer = res.entity.buffer,
				sha1 = sha1hex(res.entity.buffer),
				response = res,
				element = eseed)
		}
	}

	def elementsData(elements: Seq[Element]): Future[Seq[ElementData]] = {
		elements.map { elementData }.pipe { Future.sequence(_) }
	}
}
