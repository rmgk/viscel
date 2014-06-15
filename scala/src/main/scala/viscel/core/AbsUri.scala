package viscel.core

import org.scalactic.Requirements._
import spray.http.Uri

import scala.language.implicitConversions

case class AbsUri(uri: Uri) {
	require(uri.isAbsolute, s"$uri is not absolute")
	override def toString = uri.toString()
}

object AbsUri {
	implicit def fromString(uri: String): AbsUri = AbsUri(Uri.parseAbsolute(/*Suri.parse(*/ uri /*).toString*/))
	implicit def fromUri(uri: Uri): AbsUri = { require(uri.isAbsolute); apply(uri) }
	implicit def toUri(absuri: AbsUri): Uri = absuri.uri
	implicit def toString(absuri: AbsUri): String = absuri.toString
}
