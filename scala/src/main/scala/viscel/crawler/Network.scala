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

  case class Blob(mediatype: MediaType, sha1: String, buffer: Array[Byte], response: HttpResponse)

  private def addReferrer(referrer: Option[Uri]) = referrer match {
    case Some(ref) => addHeader("Referer" /*[sic, http spec]*/ , ref.toString())
    case None => (x: HttpRequest) => x
  }

  private def grabStats(response: Future[HttpResponse]): Future[HttpResponse] = response.andThen {
    case Success(res) => ConfigNode().download(
      size = res.entity.data.length,
      success = res.status.isSuccess,
      compressed = res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.gzip)
    case Failure(_) => ConfigNode().download(0, success = false)
  }.map { decode(Gzip) ~> decode(Deflate) }

  def getResponse(uri: Uri, referrer: Option[Uri] = None): SendReceive => Future[HttpResponse] = iopipe => {
    val pipeline = addReferrer(referrer) ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe
    logger.info(s"get $uri ($referrer)")
    grabStats(pipeline(Get(uri)))
  }

  def getDocument(uri: Uri): SendReceive => Future[Document] = getResponse(uri).andThen {
    _.map { res =>
      Jsoup.parse(
        res.entity.asString(defaultCharset = HttpCharsets.`UTF-8`),
        res.header[Location].fold(ifEmpty = uri)(_.uri).toString())
    }
  }

  def getBlob(asset: Asset): SendReceive => Future[Blob] = getResponse(asset.source, Some(asset.origin)).andThen {
    _.map { res =>
      val bytes = res.entity.data.toByteArray
      Blob(
        mediatype = res.header[`Content-Type`].get.contentType.mediaType,
        buffer = bytes,
        sha1 = sha1hex(bytes),
        response = res)
    }
  }


}
