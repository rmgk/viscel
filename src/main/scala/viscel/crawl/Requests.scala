package viscel.crawl

import java.time.Instant

import viscel.store.v4.DataRow
import viscel.store.{Vurl, WithReferer}

import scala.concurrent.Future

case class VRequest(link: DataRow.Link, origin: Option[Vurl] = None) {
  def href = link.ref
}
object VRequest {
  def from(withReferer: WithReferer) = VRequest(withReferer.link, Some(withReferer.referer))
}
case class VResponse[T](content: T,
                        location: Vurl,
                        mime: String,
                        lastModified: Option[Instant],
                        etag: Option[String])

trait WebRequestInterface {
  def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]]
}

