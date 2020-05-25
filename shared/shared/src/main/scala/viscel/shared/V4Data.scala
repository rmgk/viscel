package viscel.shared

import java.time.Instant
//import io.lemonlabs.uri.AbsoluteUrl

import viscel.shared.DataRow._


final class Vurl private(private val uri: String) extends AnyVal {
  def uriString(): String = uri
  override def toString: String = s"Vurl($uriString)"
}

object Vurl {



  def apply(s: String): Vurl = fromString(s)

  /* Ensure urls are always parsed. */
  implicit def fromString(uri: String): Vurl = {
    if (uri.startsWith("viscel:")) new Vurl(uri)
    else new Vurl(uri)
  }
}


final case class DataRow(ref: Vurl,
                         loc: Option[Vurl] = None,
                         lastModified: Option[Instant] = None,
                         etag: Option[String] = None,
                         contents: List[Content]
                        ) {
  def updates(other: DataRow): Boolean = {
    contents != other.contents
  }

}

object DataRow {
  sealed trait Content
  final case class Link(ref: Vurl, data: List[String] = Nil) extends Content
  final case class Blob(sha1: String, mime: String) extends Content
  final case class Chapter(name: String) extends Content
}
