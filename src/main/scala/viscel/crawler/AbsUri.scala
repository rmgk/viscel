package viscel.crawler

import org.scalactic.Requirements._
import spray.http.Uri

import scala.language.implicitConversions

class AbsUri private(val uri: Uri) extends AnyVal {
	override def toString = uri.toString()
}

object AbsUri {
	implicit def fromString(uri: String): AbsUri = new AbsUri(Uri.parseAbsolute(uri))
	implicit def fromUri(uri: Uri): AbsUri = { require(uri.isAbsolute); new AbsUri(uri) }
	implicit def toUri(absuri: AbsUri): Uri = absuri.uri
	implicit def toString(absuri: AbsUri): String = absuri.toString
}
