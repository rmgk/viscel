package viscel.shared

import scala.collection.immutable.Map

/** The [[name]] and [[size]] of a collection. [[missingNarrator]] is false if it can still be downloaded. */
final case class Description(id: String, name: String, size: Int, missingNarrator: Boolean)
final case class ChapterPos(name: String, pos: Int)
final case class ImageRef(
	origin: String,
	blob: Option[Blob] = None,
	data: Map[String, String] = Map())
final case class Contents(gallery: Gallery[ImageRef], chapters: List[ChapterPos])
final case class Blob(sha1: String, mime: String)
