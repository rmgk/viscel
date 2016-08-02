package viscel.neoadapter

import java.net.URL

import viscel.scribe.{Normal, Volatile, Policy}
import viscel.shared.Blob

sealed trait NeoStory

final case class More(
	loc: URL,
	policy: Policy = Normal,
	data: List[String] = List()) extends NeoStory

final case class Asset(
	blob: Option[URL] = None,
	origin: Option[URL] = None,
	kind: Byte,
	data: List[String] = List()) extends NeoStory

final case class Page(asset: Asset, blob: Option[Blob])

object GetPolicy {
	def int(s: Option[Byte]): Policy = s match {
		case None => Normal
		case Some(0) => Volatile
		case Some(s) => throw new IllegalStateException(s"unknown policy $s")
	}
}



