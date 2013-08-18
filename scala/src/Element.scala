package viscel

import java.security.MessageDigest
import viscel.display.ElementDisplay

case class Element(
	blob: String,
	mediatype: String,
	source: String,
	origin: String,
	alt: Option[String] = None,
	title: Option[String] = None,
	width: Option[Int] = None,
	height: Option[Int] = None
) extends ElementDisplay {
	def toMap = {
		Map(
				"blob" -> blob,
				"mediatype" -> mediatype,
				"source" -> source,
				"origin" -> origin
		) ++
		alt.map{"alt" -> _} ++
		title.map{"title" -> _} ++
		width.map{"width" -> _} ++
		height.map{"height" -> _}
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
		Element(blob = blob, mediatype = mediatype, source = source, origin = origin, alt = alt, title = title, width = width, height = height)
}
