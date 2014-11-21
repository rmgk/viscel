package viscel.shared

import java.net.URI
import upickle.Js

import scala.language.implicitConversions

class AbsUri private(val uri: URI) extends AnyVal {
	override def toString = uri.toString
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

	implicit val absuriWriter: upickle.Writer[AbsUri] = upickle.Writer[AbsUri]{ uri => Js.Str(toString(uri))}
	implicit val absuriReader: upickle.Reader[AbsUri] = upickle.Reader[AbsUri]{ case Js.Str(str) => fromString(str) }

}
