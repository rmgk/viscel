package viscel.core

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import spray.client.pipelining._
import spray.http.HttpHeaders.{Location, `Accept-Encoding`, `Content-Type`}
import spray.http.{HttpCharsets, HttpEncodings, HttpRequest, HttpResponse, MediaType, Uri}
import spray.httpx.encoding._
import viscel._
import viscel.description.Asset
import viscel.store._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util._

trait NetworkPrimitives extends StrictLogging {

	case class Blob(mediatype: MediaType, sha1: String, buffer: Array[Byte], response: HttpResponse)

	def iopipe: SendReceive

	def handleMoved(future: Future[HttpResponse], uri: Uri, referrer: Option[Uri]): Future[HttpResponse] = future.flatMap { res =>
		val headLoc = res.header[Location]
		res.status.intValue match {
			case 301 | 302 if headLoc.nonEmpty =>
				val newLoc = headLoc.get.uri.resolvedAgainst(uri)
				logger.info(s"new location $newLoc old ($uri)")
				getResponse(newLoc, referrer)
			case 200 => Future.successful(res)
			case _ => Future.failed(new Throwable(s"invalid response ${ res.status }; $uri ($referrer)"))
		}
	}

	def getResponse(uri: Uri, referrer: Option[Uri] = None): Future[HttpResponse] = {
		logger.info(s"get $uri ($referrer)")
		val addReferrer = referrer match {
			case Some(ref) => addHeader("Referer" /*[sic, http spec]*/ , ref.toString())
			case None => (x: HttpRequest) => x
		}
		val pipeline = addReferrer ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe

		val decodedResponse = pipeline(Get(uri)).andThen {
			case Success(res) => ConfigNode().download(
				size = res.entity.data.length,
				success = res.status.isSuccess,
				compressed = res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.gzip)
			case Failure(_) => ConfigNode().download(0, success = false)
		}.map { decode(Gzip) ~> decode(Deflate) }

		handleMoved(decodedResponse, uri, referrer)

	}

	def getDocument(uri: Uri): Future[Document] = getResponse(uri).map { res =>
		Jsoup.parse(
			res.entity.asString(defaultCharset = HttpCharsets.`UTF-8`),
			res.header[Location].fold(ifEmpty = uri)(_.uri).toString())
	}

	def getBlob(asset: Asset): Future[Blob] = {
		getResponse(asset.source, Some(asset.origin)).map { res =>
			val bytes = res.entity.data.toByteArray
			Blob(
				mediatype = res.header[`Content-Type`].get.contentType.mediaType,
				buffer = bytes,
				sha1 = sha1hex(bytes),
				response = res)
		}
	}
}
