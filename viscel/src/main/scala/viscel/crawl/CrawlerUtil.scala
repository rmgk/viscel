package viscel.crawl

import java.net.URL

import akka.http.scaladsl.model.headers.{HttpEncodings, Location, Referer, `Accept-Encoding`}
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import viscel.scribe.{BlobStore, Vurl}
import viscel.shared.Log
import viscel.shared.Blob

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try


class CrawlerUtil(blobs: BlobStore)(implicit ec: ExecutionContext, materializer: Materializer) {


	def getResponse(request: HttpRequest, iopipe: HttpRequest => Future[HttpResponse]): Future[HttpResponse] = {
		val result: Future[HttpResponse] = iopipe(request).flatMap(_.toStrict(FiniteDuration(300, SECONDS)))
		Log.info(s"get ${request.uri} (${request.header[Referer]})")
		result//.andThen(PartialFunction(responseHandler))
	}

	def request[R](source: Vurl, origin: Option[Uri] = None): HttpRequest = {
		HttpRequest(
			method = HttpMethods.GET,
			uri = source.uri,
			headers =
				`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip) ::
				origin.map(x => Referer.apply(x)).toList)
	}

	def awaitEntity(res: HttpResponse): HttpEntity.Strict = Await.result(res.entity.toStrict(FiniteDuration(5, SECONDS)), FiniteDuration(5, SECONDS))

	def parseDocument(absUri: Vurl)(res: HttpResponse): Document = Jsoup.parse(
		Await.result(Unmarshal(res).to[String], FiniteDuration(5, SECONDS)),
		res.header[Location].fold(ifEmpty = absUri.uri)(_.uri).toString())

	def parseBlob[R](res: HttpResponse): Blob = {
		val bytes = awaitEntity(res).data.toArray[Byte]
		val sha1 = blobs.write(bytes)
		Blob(
			sha1 = sha1,
			mime = res.entity.contentType.mediaType.toString())
	}

}
