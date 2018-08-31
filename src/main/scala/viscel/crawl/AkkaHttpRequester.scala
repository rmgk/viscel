package viscel.crawl

import java.time.Instant

import akka.http.javadsl.model.headers.LastModified
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.coding.{Deflate, Gzip}
import akka.http.scaladsl.model.headers.{HttpEncodings, Location, Referer, `Accept-Encoding`}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import viscel.shared.Log
import viscel.store.Vurl

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}


class AkkaHttpRequester(ioHttp: HttpExt)
                       (implicit val ec: ExecutionContext, materializer: Materializer)
  extends WebRequestInterface {

  val timeout = FiniteDuration(300, SECONDS)

  private def decompress(r: HttpResponse): Future[HttpResponse] =
    Deflate.decodeMessage(Gzip.decodeMessage(r)).toStrict(timeout)
  private def requestDecompressed(request: HttpRequest): Future[HttpResponse] =
    ioHttp.singleRequest(request.withHeaders(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)))
    .flatMap(decompress)

  def extractResponseLocation(base: Vurl, httpResponse: HttpResponse): Vurl =
    httpResponse.header[Location].fold(base)(l => Vurl.fromUri(l.uri.resolvedAgainst(base.uri)))
  def extractLastModified(httpResponse: HttpResponse): Option[Instant] =
    httpResponse.header[LastModified].map(h => Instant.ofEpochMilli(h.date().clicks()))


  private def requestWithRedirects(request: HttpRequest, redirects: Int = 10): Future[HttpResponse] = {
    Log.Crawl.info(s"request ${request.uri}" + request.header[Referer].fold("")(r => s" ($r)"))

    requestDecompressed(request).flatMap { response =>
      if (response.status.isRedirection() && response.header[Location].isDefined) {
        val loc = response.header[Location].get.uri.resolvedAgainst(request.uri)
        requestWithRedirects(request.withUri(loc), redirects = redirects - 1)
      }
      else if (response.status.isSuccess()) {
        // if the response has no location header, we insert the url from the request as a location,
        // this allows all other systems to use the most accurate location available
        Future.successful(
          response.addHeader(Location.apply(
            extractResponseLocation(Vurl.fromUri(request.uri), response).uri)))
      }
      else Future.failed(RequestException(request.uri.toString(), response.status.toString()))
    }
  }


  private def requestInternal[R](request: VRequest): Future[VResponse[HttpResponse]] = {
    val req = HttpRequest(
      method = HttpMethods.GET,
      uri = request.href.uri,
      headers = request.origin.map(x => Referer.apply(x.uri)).toList)
    requestWithRedirects(req).map{ resp =>
      VResponse(resp,
                location = extractResponseLocation(request.href, resp),
                mime = resp.entity.contentType.mediaType.toString(),
                lastModified = extractLastModified(resp))
    }
  }


  override def getString(request: VRequest): Future[VResponse[String]] = {
    for {
      resp <- requestInternal(request)
      html <- Unmarshal(resp.content).to[String]
    }
      yield resp.copy(content = html)
  }

  override def getBytes(request: VRequest): Future[VResponse[Array[Byte]]] = {
    for {
      resp <- requestInternal(request)
      entity <- resp.content.entity.toStrict(timeout) //should be strict already, but we do not know here
    }
      yield resp.copy(content = entity.data.toArray[Byte])

  }
}
