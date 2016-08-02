package viscel.neoadapter

import viscel.scribe.{Normal, Policy, Volatile, Vuri}
import viscel.shared.Blob

sealed trait NeoStory

final case class More(
	loc: Vuri,
	policy: Policy = Normal,
	data: List[String] = List()) extends NeoStory

final case class Asset(
	blob: Option[Vuri] = None,
	origin: Option[Vuri] = None,
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



