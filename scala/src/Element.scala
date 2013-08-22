package viscel

import java.security.MessageDigest
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
	height: Option[Int] = None) {

	def toMap = {
		Map(
			"blob" -> blob,
			"mediatype" -> mediatype,
			"source" -> source,
			"origin" -> origin) ++
			alt.map { "alt" -> _ } ++
			title.map { "title" -> _ } ++
			width.map { "width" -> _ } ++
			height.map { "height" -> _ }
	}
}
