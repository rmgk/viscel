package viscel.core

import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import scalax.io._
import spray.can.Http
import spray.client.pipelining._
import spray.http.ContentType
import spray.http.HttpHeaders.`Accept-Encoding`
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpEncodings
import spray.http.HttpResponse
import spray.http.Uri
import spray.httpx.encoding._
import viscel._
import viscel.store._

trait NetworkPrimitives extends Logging {

	def iopipe: SendReceive

	def response(uri: Uri, referer: Option[Uri] = None): Future[HttpResponse] = {
		logger.info(s"get $uri ($referer)")
		val addReferer = referer match {
			case Some(ref) => addHeader("Referer", ref.toString)
			case None => (x: HttpRequest) => x
		}
		Get(uri).pipe {
			addReferer ~> addHeader(`Accept-Encoding`(HttpEncodings.gzip, HttpEncodings.deflate)) ~>
				iopipe ~> decode(Gzip) ~> decode(Deflate)
		}
			.flatMap { res =>
				res.validate(
					_.status.intValue == 200,
					FailRun(s"invalid response ${res.status}; $uri ($referer)")).toFuture
			}
	}

	def document(uri: Uri): Future[Document] = response(uri).map { res =>
		Jsoup.parse(
			res.entity.asString,
			res.header[Location].map { _.uri }.getOrElse(uri).toString)
	}

	def elementData(edesc: ElementDescription): Future[ElementData] = {
		response(edesc.source, Some(edesc.origin)).map { res =>
			ElementData(
				mediatype = res.header[`Content-Type`].get.contentType,
				buffer = res.entity.buffer,
				sha1 = sha1hex(res.entity.buffer),
				response = res,
				description = edesc)
		}
	}

	def elementsData(elements: Seq[ElementDescription]): Future[Seq[ElementData]] = {
		elements.map { elementData }.pipe { Future.sequence(_) }
	}
}
