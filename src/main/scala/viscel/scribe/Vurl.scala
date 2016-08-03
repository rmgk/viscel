package viscel.scribe

import java.net.URL

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path
import upickle.default.{Reader, Writer}
import viscel.shared.Blob

import scala.language.implicitConversions

final class Vurl(val uri: Uri) {
	override def toString: String = uri.toString()
	override def hashCode(): Int = uri.hashCode()
	override def equals(other: scala.Any): Boolean = other match {
		case o: Vurl => uri.equals(o.uri)
		case _ => false
	}
}

object Vurl {

	implicit val uriReader: Reader[Vurl] = Reader[Vurl] {
		case upickle.Js.Str(str) => fromString(str)
	}
	implicit val uriWriter: Writer[Vurl] = Writer[Vurl] { url => upickle.Js.Str(url.toString) }

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
	/* we use the parser from java.net.URL as that handles unicode characters in the path better,
	 * except if the thing is a viscel: uri, which is not a legal URL, so can not be parsed by that */
	implicit def fromString(uri: String): Vurl = {
		if (uri.startsWith("viscel:"))
			new Vurl(Uri.parseAbsolute(uri))
		else {
			new Vurl(urlToUri(new URL(uri)))
		}
	}

	val entrypoint: Vurl = new Vurl(Uri(scheme = "viscel", path = Path("/initial")))
	def blobPlaceholder(blob: Blob) = new Vurl(Uri(scheme = "viscel", path = Path(s"/sha1/${blob.sha1}")))
}
