package viscel.crawl

import java.time.Instant

import akka.http.javadsl.model.headers.LastModified
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.coding.{Deflate, Gzip}
import akka.http.scaladsl.model.headers.{HttpEncodings, Location, Referer, `Accept-Encoding`}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import viscel.scribe.{ScribeBlob, Vurl}
import viscel.shared.{Blob, Log}
import viscel.store.BlobStore

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}


class RequestUtil(blobs: BlobStore, ioHttp: HttpExt)(implicit val ec: ExecutionContext, materializer: Materializer) {

	val timeout = FiniteDuration(300, SECONDS)

	def getResponse(request: HttpRequest, redirects: Int = 10): Future[HttpResponse] = {
		Log.info(s"request ${request.uri} (${request.header[Referer]})")
		ioHttp.singleRequest(request)
			.flatMap(_.toStrict(timeout))
			.flatMap { res =>
				if (res.status.isRedirection() && res.header[Location].isDefined) {
					val loc = res.header[Location].get.uri.resolvedAgainst(request.uri)
					getResponse(request.withUri(loc), redirects = redirects - 1)
				}
				else if (res.status.isSuccess()) {
					// if the response has no location header, we insert the url from the request as a location,
					// this allows all other systems to use the most accurate location available
					val resWithLocation = res.addHeader(Location.apply(extractResponseLocation(Vurl.fromUri(request.uri), res).uri))
					Future.successful(resWithLocation)
				}
				else {Future.failed(RequestException(request, res))}
			}
			.map(r => Deflate.decodeMessage(Gzip.decodeMessage(r)))
	}

	def extractResponseLocation(base: Vurl, httpResponse: HttpResponse): Vurl = {
		httpResponse.header[Location].fold(base)(l => Vurl.fromUri(l.uri.resolvedAgainst(base.uri)))
	}

	def extractLastModified(httpResponse: HttpResponse): Option[Instant] = {
		httpResponse.header[LastModified].map(h => Instant.ofEpochMilli(h.date().clicks()))
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

	def extractDocument(baseuri: Vurl)(httpResponse: HttpResponse): Future[Document] = {
		Unmarshal(httpResponse).to[String].map { html =>
			val responseLocation = extractResponseLocation(baseuri, httpResponse)
			Jsoup.parse(html, responseLocation.uriString())
		}
	}


	def requestBlob[R](source: Vurl, origin: Option[Vurl] = None): Future[ScribeBlob] = {
		request(source, origin).flatMap { res =>
			res.entity.toStrict(timeout).map { entity =>
				val bytes = entity.data.toArray[Byte]
				val sha1 = blobs.write(bytes)
				ScribeBlob(source, extractResponseLocation(source, res),
					blob = Blob(
						sha1 = sha1,
						mime = res.entity.contentType.mediaType.toString()),
					date = extractLastModified(res).getOrElse(Instant.now()))
			}
		}
	}

}
