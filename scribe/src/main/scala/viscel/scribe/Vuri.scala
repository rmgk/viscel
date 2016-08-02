package viscel.scribe

import java.net.URL

import akka.http.scaladsl.model.Uri
import upickle.default.{Reader, Writer}


import scala.language.implicitConversions

class Vuri private(val uri: Uri) {
	override def toString: String = uri.toString()
}

object Vuri {

	implicit val uriReader: Reader[Vuri] = Reader[Vuri] {
		case upickle.Js.Str(str) => fromString(str)
	}
	implicit val uriWriter: Writer[Vuri] = Writer[Vuri] { url => upickle.Js.Str(url.toString) }

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

	implicit def fromString(uri: String): Vuri = new Vuri(urlToUri(new URL(uri)))
}
