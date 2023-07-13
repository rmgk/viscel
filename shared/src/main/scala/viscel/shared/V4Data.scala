package viscel.shared

import java.net.URL
import java.time.Instant
import viscel.shared.DataRow.*

import scala.annotation.nowarn

final class Vurl private (private val uri: String) extends AnyVal derives CanEqual {
  def uriString(): String       = uri
  override def toString: String = s"Vurl(${uriString()})"
}

object Vurl {

  def apply(s: String): Vurl = fromString(s)

  /* Ensure urls are always parsed. */
  implicit def fromString(uri: String): Vurl = {
    if (uri.startsWith("viscel:")) new Vurl(uri)
    else
      // as far as the deprecation below states, the URL constructor does not do that much parsing.
      // however, that seems to be excactly what we want? The last time I checked, the URI constructor complains about a lot of things, that turn out to not be any issue when resolving the URL.
      new Vurl((new URL(uri): @nowarn).toString)
  }

  def unsafeFromString(uri: String): Vurl = {
    new Vurl(uri)
  }
}

final case class DataRow(
    ref: Vurl,
    loc: Option[Vurl] = None,
    lastModified: Option[Instant] = None,
    etag: Option[String] = None,
    contents: List[Content]
) derives CanEqual {
  def updates(other: DataRow): Boolean = {
    contents != other.contents
  }

}

object DataRow {
  sealed trait Content derives CanEqual
  final case class Link(ref: Vurl, data: List[String] = Nil) extends Content
  final case class Blob(sha1: String, mime: String)          extends Content
  final case class Chapter(name: String)                     extends Content
}
