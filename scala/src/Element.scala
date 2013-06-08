package viscel

import java.security.MessageDigest
import viscel.display.ElementDisplay

case class Element(
	blob: String,
	id: String,
	mediatype: String,
	source: String,
	origin: String,
	alt: Option[String] = None,
	title: Option[String] = None,
	width: Option[Int] = None,
	height: Option[Int] = None
) extends ElementDisplay

object Element {

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

class ElementSeed(
	val source: String,
	val origin: String,
	val alt: Option[String] = None,
	val title: Option[String] = None,
	val width: Option[Int] = None,
	val height: Option[Int] = None
) extends ((String, String) => Element) {
	def apply(blob: String, mediatype: String) =
		Element.fromData(blob = blob, mediatype = mediatype, source = source, origin = origin, alt = alt, title = title, width = width, height = height)
}
