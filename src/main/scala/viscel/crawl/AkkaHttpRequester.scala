package viscel.crawl

import java.time.Instant

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.coding.{Deflate, Gzip}
import akka.http.scaladsl.model.headers.{ETag, HttpEncodings, Location, Referer, `Accept-Encoding`, `Last-Modified`}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import cats.implicits.catsSyntaxEitherId
import viscel.netzi.Vurl.fromString
import viscel.netzi.{VRequest, VResponse, Vurl, WebRequestInterface}
import viscel.shared.Log

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}


class AkkaHttpRequester(ioHttp: HttpExt)
                       (implicit val ec: ExecutionContext, materializer: Materializer)
  extends WebRequestInterface {

  val timeout = FiniteDuration(300, SECONDS)


  def vurlToUri(vin: Vurl): Uri = Uri.parseAbsolute(vin.uriString())

  def uriToVurl(uri: Uri): Vurl = {
    if (!uri.isAbsolute) throw new IllegalArgumentException(s"$uri is not absolute")
    fromString(uri.toString())
  }


  private def decompress(r: HttpResponse): Future[HttpResponse] =
    Deflate.decodeMessage(Gzip.decodeMessage(r)).toStrict(timeout)
  private def requestDecompressed(request: HttpRequest): Future[HttpResponse] = {
    val finalRequest = request.addHeader(`Accept-Encoding`(HttpEncodings.deflate,
                                                             HttpEncodings.gzip))
    Log.Crawl.info(s"request ${finalRequest.uri}" + finalRequest.header[Referer].fold("")(r => s" ($r)"))

    ioHttp.singleRequest(finalRequest).flatMap(decompress)
  }

  def extractResponseLocation(base: Vurl, httpResponse: HttpResponse): Vurl =
    httpResponse.header[Location].fold(base)(l => uriToVurl(l.uri.resolvedAgainst(vurlToUri(base))))
  def extractLastModified(httpResponse: HttpResponse): Option[Instant] =
    httpResponse.header[`Last-Modified`].map(h => Instant.ofEpochMilli(h.date.clicks))
  def extractEtag(httpResponse: HttpResponse): Option[String] =
    httpResponse.header[ETag].map(_.toString())


  private def requestWithRedirects(request: HttpRequest, redirects: Int = 10): Future[HttpResponse] = {
    requestDecompressed(request).flatMap { response =>
      if (response.status.isRedirection() && response.header[Location].isDefined) {
        val loc = response.header[Location].get.uri.resolvedAgainst(request.uri)
        requestWithRedirects(request.withUri(loc), redirects = redirects - 1)
      }
      else if (response.status.isSuccess()) {
        // if the response has no location header, we insert the url from the request as a location,
        // this allows all other systems to use the most accurate location available
        Future.successful(
          response.addHeader(Location.apply(vurlToUri(
            extractResponseLocation(uriToVurl(request.uri), response)))))
      }
      else Future.failed(RequestException(request.uri.toString(), response.status.toString()))
    }
  }


  private def requestInternal[R](request: VRequest): Future[VResponse[HttpResponse]] = {
    val req = HttpRequest(
      method = HttpMethods.GET,
      uri = vurlToUri(request.href),
      headers = request.origin.map(x => Referer.apply(vurlToUri(x))).toList)
    requestWithRedirects(req).map{ resp =>
      VResponse(resp,
                location = extractResponseLocation(request.href, resp),
                mime = resp.entity.contentType.mediaType.toString(),
                lastModified = extractLastModified(resp),
                etag = extractEtag(resp))
    }
  }


  override def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]] = {
    for {
      resp <- requestInternal(request)
      content <- if (!resp.content.entity.contentType.binary)
                   Unmarshal(resp.content).to[String].map(_.asRight)
                 else resp.content.entity.toStrict(timeout).map(_.data.toArray[Byte].asLeft)
    } yield resp.copy(content = content)
  }

}
