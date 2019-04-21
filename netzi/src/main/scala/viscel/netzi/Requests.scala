package viscel.netzi

import java.time.Instant

import scala.concurrent.Future

case class VRequest(href: Vurl, context: Seq[String] = Nil, origin: Option[Vurl] = None) {
  def ref = href
  def data = context
}
case class VResponse[T](content: T,
                        location: Vurl,
                        mime: String,
                        lastModified: Option[Instant],
                        etag: Option[String])

trait WebRequestInterface {
  def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]]
}

