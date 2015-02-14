package viscel.shared

import scala.collection.immutable.Map

final case class Chapter(name: String, pos: Int)
final case class Description(id: String, name: String, size: Int)
final case class Article(
	source: Option[String] = None,
	origin: Option[String] = None,
	blob: Option[String] = None,
	mime: Option[String] = None,
	data: Map[String, String] = Map())
final case class Content(gallery: Gallery[Article], chapters: List[Chapter])