package viscel.crawl

import java.time.Instant

import viscel.store.Vurl
import viscel.store.v4.DataRow

import scala.concurrent.Future

case class VRequest(link: DataRow.Link, origin: Option[Vurl] = None) {
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

