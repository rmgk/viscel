package viscel.crawl

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.headers.{HttpEncodings, Location, Referer, `Accept-Encoding`}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import viscel.scribe.{BlobStore, Vurl}
import viscel.shared.{Blob, Log}

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}


class RequestUtil(blobs: BlobStore, ioHttp: HttpExt)(implicit val ec: ExecutionContext, materializer: Materializer) {

	val timeout = FiniteDuration(300, SECONDS)

	def getResponse(request: HttpRequest): Future[HttpResponse] = {
		val result: Future[HttpResponse] = ioHttp.singleRequest(request).flatMap(_.toStrict(timeout))
		Log.info(s"get ${request.uri} (${request.header[Referer]})")
		result //.andThen(PartialFunction(responseHandler))
	}

	def request[R](source: Vurl, origin: Option[Vurl] = None): Future[HttpResponse] = {
		val req = HttpRequest(
			method = HttpMethods.GET,
			uri = source.uri,
			headers =
				`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip) ::
					origin.map(x => Referer.apply(x.uri)).toList)
		getResponse(req)
	}

	def requestDocument(source: Vurl, origin: Option[Vurl] = None): Future[Document] = {
		request(source, origin).flatMap { res =>
			Unmarshal(res).to[String].map { html =>
				Jsoup.parse(html, res.header[Location].fold(ifEmpty = source.uri)(_.uri).toString())
			}
		}
	}


	def requestBlob[R](source: Vurl, origin: Option[Vurl] = None): Future[Blob] = {
		request(source, origin).flatMap { res =>
			res.entity.toStrict(timeout).map { entity =>
				val bytes = entity.data.toArray[Byte]
				val sha1 = blobs.write(bytes)
				Blob(
					sha1 = sha1,
					mime = res.entity.contentType.mediaType.toString())
			}
		}
	}

}
