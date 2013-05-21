package viscel

import java.security.MessageDigest
import viscel.display.ElementDisplay

case class Element(
	blob: String,
	id: String,
	mediatype: String,
	source: String,
	origin: String,
	alt: Option[String],
	title: Option[String],
	width: Option[Int],
	height: Option[Int]
) extends ElementDisplay

object Element {

	lazy val digester = MessageDigest.getInstance("SHA1")
	def sha1hex(b: Array[Byte]) = (digester.digest(b) map {"%02X" format _}).mkString

	def fromData(
		blob: String,
		mediatype: String,
		source: String,
		origin: String,
		alt: Option[String] = None,
		title: Option[String] = None,
		width: Option[Int] = None,
		height: Option[Int] = None
	): Element = {
		val idstring = (List(blob, mediatype, source, origin) ++ (List(alt, title, width, height) map {_.getOrElse("")})) mkString "\n"
		val id = sha1hex(idstring.getBytes("UTF8"))
		Element(id = id, blob = blob, mediatype = mediatype, source = source, origin = origin, alt = alt, title = title, width = width, height = height)
	}
}
