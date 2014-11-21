package viscel.shared

import java.net.URI

import scala.language.implicitConversions

class AbsUri private(val uri: URI) extends AnyVal {
	override def toString = uri.toString()
}

object AbsUri {
	implicit def fromString(uri: String): AbsUri = {
		val parsed = new URI(uri)
		Predef.require(parsed.isAbsolute)
		new AbsUri(parsed)
	}
	implicit def fromUri(uri: URI): AbsUri = { Predef.require(uri.isAbsolute); new AbsUri(uri) }
	implicit def toUri(absuri: AbsUri): URI = absuri.uri
	implicit def toString(absuri: AbsUri): String = absuri.toString
}
