package viscel.newCore

//import com.github.theon.uri.{ Uri => Suri }
import scala.language.implicitConversions
import spray.http.Uri

case class AbsUri(uri: Uri) {
	override def toString = uri.toString()
}

object AbsUri {
	implicit def fromString(uri: String): AbsUri = AbsUri(Uri.parseAbsolute( /*Suri.parse(*/ uri /*).toString*/ ))
	implicit def fromUri(uri: Uri): AbsUri = { require(uri.isAbsolute); apply(uri) }
	implicit def toUri(absuri: AbsUri): Uri = absuri.uri
	implicit def toString(absuri: AbsUri): String = absuri.toString
}
