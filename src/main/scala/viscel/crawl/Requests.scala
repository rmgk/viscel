package viscel.crawl

import java.time.Instant

import viscel.scribe.Vurl

import scala.concurrent.Future

case class VRequest(href: Vurl, origin: Option[Vurl] = None)
case class VResponse[T](content: T, request: VRequest, location: Vurl, mime: String, lastModified: Option[Instant])

trait WebRequestInterface {
  def getString(request: VRequest): Future[VResponse[String]]
  def getBytes(request: VRequest): Future[VResponse[Array[Byte]]]
}

