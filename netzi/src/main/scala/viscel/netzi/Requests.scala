package viscel.netzi

import java.time.Instant

import scala.concurrent.Future

case class VRequest(link: Link, origin: Option[Vurl] = None) {
  def href = link.ref
}
case class VResponse[T](content: T,
                        location: Vurl,
                        mime: String,
                        lastModified: Option[Instant],
                        etag: Option[String])

trait WebRequestInterface {
  def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]]
}

