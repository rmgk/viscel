package viscel.shared

import scala.collection.immutable.Map

/** The [[name]] and [[size]] of a collection. [[unknownNarrator]] is false if it can still be downloaded. */
final case class Description(id: Vid, name: String, size: Int, unknownNarrator: Boolean)
final case class ChapterPos(name: String, pos: Int)
final case class SharedImage(origin: String,
                             blob: Option[Blob] = None,
                             data: Map[String, String] = Map())
final case class Contents(gallery: Gallery[SharedImage], chapters: List[ChapterPos])
final case class Blob(sha1: String, mime: String)
