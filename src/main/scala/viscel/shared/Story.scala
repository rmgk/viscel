package viscel.shared

import scala.collection.immutable.Map
import scala.language.implicitConversions
import upickle.key

sealed trait Story

object Story {
	@key("More") final case class More(loc: AbsUri, pagetype: String, narration: List[Story] = Nil) extends Story
	@key("Chapter") final case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Story
	@key("Asset") final case class Asset(source: AbsUri, origin: AbsUri, metadata: Map[String, String] = Map(), blob: Option[Blob] = None) extends Story
	@key("Core") final case class Core(kind: String, id: String, name: String, metadata: Map[String, String]) extends Story
	@key("Failed") final case class Failed(reason: List[String]) extends Story

	@key("Blob") final case class Blob(sha1: String, mediatype: String)
}
