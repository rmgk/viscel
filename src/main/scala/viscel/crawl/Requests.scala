package viscel.crawl

import java.time.Instant

import viscel.scribe.Vurl

import scala.concurrent.Future

sealed trait VRequest {
  val href: Vurl
  val origin: Option[Vurl]
}
object VRequest {
  case class Text(href: Vurl, origin: Option[Vurl] = None)(val handler: VResponse[String] => List[VRequest]) extends VRequest
  case class Blob(href: Vurl, origin: Option[Vurl] = None)(val handler: VResponse[Array[Byte]] => List[VRequest]) extends VRequest
}
case class VResponse[T](content: T, request: VRequest, location: Vurl, mime: String, lastModified: Option[Instant])

trait WebRequestInterface {
  def get(request: VRequest.Text): Future[VResponse[String]]
  def get(request: VRequest.Blob): Future[VResponse[Array[Byte]]]
}
