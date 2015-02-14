package viscel.compat.v1

import upickle.Js

import scala.language.implicitConversions

class ViscelUrl(val self: String) extends AnyVal {
	override def toString: String = self
}

object ViscelUrl {
	implicit val absuriWriter: upickle.Writer[ViscelUrl] = upickle.Writer[ViscelUrl] { uri => Js.Str(uri.self) }
	implicit val absuriReader: upickle.Reader[ViscelUrl] = upickle.Reader[ViscelUrl] { case Js.Str(str) => new ViscelUrl(str) }
}
