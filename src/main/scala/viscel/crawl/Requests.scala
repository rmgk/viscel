package viscel.crawl

import java.time.Instant

import viscel.store.Vurl

import scala.concurrent.Future

case class VRequest(href: Vurl, origin: Option[Vurl] = None)
case class VResponse[T](content: T,
                        location: Vurl,
                        mime: String,
                        lastModified: Option[Instant],
                        etag: Option[String])

trait WebRequestInterface {
  def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]]
}

