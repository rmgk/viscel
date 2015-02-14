package viscel.scribe.narration

import java.net.URL

import scala.language.implicitConversions

sealed trait Story

final case class More(
	loc: URL,
	policy: Policy = Normal,
	data: List[String] = List()) extends Story

final case class Asset(
	blob: Option[URL] = None,
	origin: Option[URL] = None,
	kind: Byte,
	data: List[String] = List()) extends Story

final case class Blob(sha1: String, mime: String)

final case class Page(asset: Asset, blob: Option[Blob])

sealed trait Policy {
	def ext: Option[Byte]
}
case object Normal extends Policy {
	override def ext: Option[Byte] = None
}
case object Volatile extends Policy {
	override def ext: Option[Byte] = Some(0)
}
object Policy {
	def int(s: Option[Byte]): Policy = s match {
		case None => Normal
		case Some(0) => Volatile
		case Some(s) => throw new IllegalStateException(s"unknown policy $s")
	}
}



