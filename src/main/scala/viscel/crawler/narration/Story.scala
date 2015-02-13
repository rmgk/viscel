package viscel.crawler.narration

import java.net.URL

import scala.language.implicitConversions

sealed trait Story
final case class More(loc: URL, policy: Policy = Normal, data: Array[String] = Array()) extends Story
final case class Asset(blob: Option[URL], origin: Option[URL], data: Array[String] = Array()) extends Story

final case class Blob(sha1: String, mime: String)
final case class Description(id: String, name: String, size: Int)

sealed trait Policy {
	def ext: Option[String]
}
case object Normal extends Policy {
	override def ext = None
}
case object Volatile extends Policy {
	override def ext = Some("V")
}
object Policy {
	def int(s: Option[String]): Policy = s match {
		case None => Normal
		case Some("V") => Volatile
		case Some(s) => throw new IllegalStateException(s"unknown policy $s")
	}
}



