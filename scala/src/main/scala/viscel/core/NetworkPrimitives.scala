package viscel.core

//import com.github.theon.uri.{ Uri => Suri }
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util._
import spray.client.pipelining._
import spray.http.HttpCharsets
import spray.http.HttpEncodings
import spray.http.HttpHeaders.Location
import spray.http.HttpHeaders.`Accept-Encoding`
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.Uri
import spray.httpx.encoding._
import viscel._
import viscel.store._
import org.scalactic.TypeCheckedTripleEquals._

trait NetworkPrimitives extends StrictLogging {

	def iopipe: SendReceive

	def handleMoved(future: Future[HttpResponse], uri: Uri, referrer: Option[Uri]): Future[HttpResponse] = future.flatMap { res =>
		val headLoc = res.header[Location]
		res.status.intValue match {
			case 301 | 302 if headLoc.nonEmpty =>
				val newLoc = headLoc.get.uri.resolvedAgainst(uri)
				logger.info(s"new location $newLoc old ($uri)")
				getResponse(newLoc, referrer)
			case 200 => Future.successful(res)
			case _ => Future.failed(new Throwable(s"invalid response ${res.status}; $uri ($referrer)"))
		}
	}

	def getResponse(uri: Uri, referrer: Option[Uri] = None): Future[HttpResponse] = {
		logger.info(s"get $uri ($referrer)")
		val addReferer = referrer match {
			case Some(ref) => addHeader("Referer" /*[sic, http spec]*/ , ref.toString())
			case None => (x: HttpRequest) => x
		}
		val pipeline = addReferer ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe

		val decodedResponse = pipeline(Get(uri)).andThen {
			case Success(res) => ConfigNode().download(res.entity.data.length, res.status.isSuccess, res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.deflate)
			case Failure(_) => ConfigNode().download(0, success = false)
		}.map { decode(Gzip) ~> decode(Deflate) }

		handleMoved(decodedResponse, uri, referrer)

	}

	def getDocument(uri: Uri): Future[Document] = getResponse(uri).map { res =>
		Jsoup.parse(
			res.entity.asString(HttpCharsets.`UTF-8`),
			res.header[Location].fold(ifEmpty = uri)(_.uri).toString())
	}

	def getElementData(edesc: ElementDescription): Future[ElementData] = {
		getResponse(edesc.source, Some(edesc.origin)).map { res =>
			val bytes = res.entity.data.toByteArray
			ElementData(
				mediatype = res.header[`Content-Type`].get.contentType,
				buffer = bytes,
				sha1 = sha1hex(bytes),
				response = res,
				description = edesc)
		}
	}

	def getElementsData(elements: Seq[ElementDescription]): Future[Seq[ElementData]] = {
		Future.sequence(elements.map { getElementData })
	}
}
