package viscel.scribe

import java.net.URL

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path
import io.circe.{Decoder, Encoder}
import viscel.shared.Blob

import scala.language.implicitConversions

final class Vurl private(val uri: Uri) extends AnyVal {
	override def toString: String = s" $uri "
	def uriString(): String = uri.toString()
}

object Vurl {

	implicit val uriReader: Decoder[Vurl] = Decoder[String].map(fromString)
	implicit val uriWriter: Encoder[Vurl] = Encoder[String].contramap[Vurl](_.uriString())

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

	def fromUri(uri: Uri): Vurl = {
		if (!uri.isAbsolute) throw new IllegalArgumentException(s"$uri is not absolute")
		new Vurl(uri)
	}

	val entrypoint: Vurl = new Vurl(Uri(scheme = "viscel", path = Path("/initial")))
	def blobPlaceholder(blob: Blob) = new Vurl(Uri(scheme = "viscel", path = Path(s"/sha1/${blob.sha1}")))
}
