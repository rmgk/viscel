package viscel.netzi

import java.time.Instant

import viscel.shared.Vurl

import scala.concurrent.Future

case class VRequest(href: Vurl, context: List[String] = Nil, referer: Option[Vurl] = None)
case class VResponse[T](content: T, location: Vurl, mime: String, lastModified: Option[Instant], etag: Option[String])

trait WebRequestInterface {
  def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]]
}
