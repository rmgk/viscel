package viscel.core

import spray.http.Uri
import org.neo4j.graphdb.Node
import viscel.store.ElementNode

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

	def similar(node: ElementNode) = {
		require(source == Uri(node[String]("source")), "source does not match")
		require(origin == Uri(node[String]("origin")), "origin does not match")
		require(alt == node.get[String]("alt"), "alt does not match")
		require(title == node.get[String]("title"), "title does not match")
		require(width == node.get[Int]("width"), "width does not match")
		require(height == node.get[Int]("height"), "height does not match")
		true
	}
}
