package viscel.netzi

import de.rmgk.delay.Async
import viscel.shared.Vurl

import java.time.Instant

case class VRequest(href: Vurl, context: List[String] = Nil, referer: Option[Vurl] = None)
case class VResponse[T](content: T, location: Vurl, mime: String, lastModified: Option[Instant], etag: Option[String])

trait WebRequestInterface {
  def get(request: VRequest): Async[Any, VResponse[Either[Array[Byte], String]]]
}
