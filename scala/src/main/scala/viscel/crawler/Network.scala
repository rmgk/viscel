package viscel.crawler

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

object Network extends StrictLogging {

	final case class DelayedRequest[R](request: HttpRequest, continue: HttpResponse => R) {
		def map[S](f: R => S): DelayedRequest[S] = copy(continue = continue.andThen(f))
	}

	final case class Blob(mediatype: MediaType, sha1: String, buffer: Array[Byte], response: HttpResponse)

	private def addReferrer(referrer: Uri): (HttpRequest) => HttpRequest = addHeader("Referer" /*[sic, http spec]*/ , referrer.toString())

	private def grabStats(response: Future[HttpResponse]): Future[HttpResponse] = response.andThen {
		case Success(res) => ConfigNode().download(
			size = res.entity.data.length,
			success = res.status.isSuccess,
			compressed = res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.gzip)
		case Failure(_) => ConfigNode().download(0, success = false)
	}

	def getResponse(request: HttpRequest): SendReceive => Future[HttpResponse] = iopipe => {
		val result = request ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe
		logger.info(s"get ${ request.uri } (${ request.headers })")
		grabStats(result).map { decode(Gzip) ~> decode(Deflate) }
	}

	def documentRequest(uri: Uri): DelayedRequest[Document] =
		DelayedRequest(
			request = Get(uri),
			continue = res => Jsoup.parse(
				res.entity.asString(defaultCharset = HttpCharsets.`UTF-8`),
				res.header[Location].fold(ifEmpty = uri)(_.uri).toString())
		)

	def blobRequest(asset: Asset): DelayedRequest[Blob] =
		DelayedRequest(
			request = Get(asset.source) ~> addReferrer(asset.origin),
			continue = { res =>
				val bytes = res.entity.data.toByteArray
				Blob(
					mediatype = res.header[`Content-Type`].get.contentType.mediaType,
					buffer = bytes,
					sha1 = sha1hex(bytes),
					response = res)
			}
		)


}
