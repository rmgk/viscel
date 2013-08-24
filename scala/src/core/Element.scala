package viscel.core

import spray.http.Uri

case class Element(
	source: Uri,
	origin: Uri,
	alt: Option[String] = None,
	title: Option[String] = None,
	width: Option[Int] = None,
	height: Option[Int] = None) {

	def toMap = {
		Map("source" -> source.toString,
			"origin" -> origin.toString) ++
			alt.map { "alt" -> _ } ++
			title.map { "title" -> _ } ++
			width.map { "width" -> _ } ++
			height.map { "height" -> _ }
	}
}
