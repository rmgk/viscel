package viscel.netzi

import java.net.URL

import scala.language.implicitConversions

/** Abstraction over possible representation for URLs.
  * Url handling is more complicated than it looks,
  * we currently represent them internally as [[akka.http.scaladsl.model.Uri]],
  * mostly because [[WebRequestInterface]] can directly use them.
  * Note that we do use [[java.net.URL]] to parse strings and then convert them manually.
  * Yes, this is because [[akka.http.scaladsl.model.Uri]] string parsing did not always work as expected,
  * and [[java.net.URL]] is more stable. */
final class Vurl private(private val uri: String) extends AnyVal {
  def uriString() = uri
  override def toString: String = s"Vurl($uriString)"
}

object Vurl {

  def apply(s: String): Vurl = fromString(s)

  /* we use the parser from java.net.URL as that handles unicode characters in the path better,
   * except if the thing is a viscel: uri, which is not a legal URL, so can not be parsed by that */
  implicit def fromString(uri: String): Vurl = {
    if (uri.startsWith("viscel:"))
      new Vurl(uri)
    else {
      new Vurl(new URL(uri).toString)
    }
  }
}
