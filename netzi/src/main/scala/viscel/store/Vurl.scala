package viscel.store

import java.net.URL

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path

import scala.language.implicitConversions

/** Abstraction over possible representation for URLs.
  * Url handling is more complicated than it looks,
  * we currently represent them internally as [[akka.http.scaladsl.model.Uri]],
  * mostly because [[WebRequestInterface]] can directly use them.
  * Note that we do use [[java.net.URL]] to parse strings and then convert them manually.
  * Yes, this is because [[akka.http.scaladsl.model.Uri]] string parsing did not always work as expected,
  * and [[java.net.URL]] is more stable. */
final class Vurl private(val uri: Uri) extends AnyVal {
  def uriString(): String = uri.toString()
  override def toString: String = s"Vurl(${uriString()})"
}

object Vurl {

  def urlToUri(in: URL): Uri = {
    implicit class X(s: String) {def ? = Option(s).getOrElse("")}
    Uri.from(
      scheme = in.getProtocol.?,
      userinfo = in.getUserInfo.?,
      host = in.getHost.?,
      port = if (in.getPort < 0) 0 else in.getPort,
      path = in.getPath.?.replaceAll("\"", ""),
      queryString = Option(in.getQuery).map(_.replaceAll("\"", "")),
      fragment = Option(in.getRef)
    )
  }

  def apply(s: String): Vurl = fromString(s)
  def apply(uri: Uri): Vurl = fromUri(uri)

  /* we use the parser from java.net.URL as that handles unicode characters in the path better,
   * except if the thing is a viscel: uri, which is not a legal URL, so can not be parsed by that */
  implicit def fromString(uri: String): Vurl = {
    if (uri.startsWith("viscel:"))
      new Vurl(Uri.parseAbsolute(uri))
    else {
      new Vurl(urlToUri(new URL(uri)))
    }
  }

  def fromUri(uri: Uri): Vurl = {
    if (!uri.isAbsolute) throw new IllegalArgumentException(s"$uri is not absolute")
    new Vurl(uri)
  }

  val entrypoint: Vurl = new Vurl(Uri(scheme = "viscel", path = Path("/initial")))
}
