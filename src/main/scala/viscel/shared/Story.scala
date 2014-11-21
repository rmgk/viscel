package viscel.shared

import scala.collection.immutable.Map
import scala.language.implicitConversions

sealed trait Story

object Story {
	final case class More(loc: AbsUri, pagetype: String, narration: List[Story] = Nil) extends Story
	final case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Story
	final case class Asset(source: AbsUri, origin: AbsUri, metadata: Map[String, String] = Map(), blob: Option[Blob] = None) extends Story
	final case class Core(kind: String, id: String, name: String, metadata: Map[String, String]) extends Story
	final case class Failed(reason: List[String]) extends Story

	final case class Blob(sha1: String, mediatype: String)
}
