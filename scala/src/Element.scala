package viscel

import java.security.MessageDigest
import viscel.display.ElementDisplay
import org.neo4j.graphdb.Node
import viscel.store.Neo

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

object Element {
	def fromNode(node: Node) = Neo.tx{ _ =>
		Element(
			blob = node.getProperty("blob").asInstanceOf[String],
			mediatype = node.getProperty("mediatype").asInstanceOf[String],
			source = node.getProperty("source").asInstanceOf[String],
			origin = node.getProperty("origin").asInstanceOf[String],
			alt = Option(node.getProperty("alt", null).asInstanceOf[String]),
			title = Option(node.getProperty("title", null).asInstanceOf[String]),
			width = Option(node.getProperty("width", null).asInstanceOf[Int]),
			height = Option(node.getProperty("height", null).asInstanceOf[Int])
		)
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
