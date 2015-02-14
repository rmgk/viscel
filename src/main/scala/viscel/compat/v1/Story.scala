package viscel.compat.v1

import viscel.shared.Gallery

import scala.collection.immutable.Map

sealed trait Story

object Story {

	final case class More(loc: ViscelUrl, kind: More.Kind) extends Story
	final case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Story
	final case class Asset(source: ViscelUrl, origin: ViscelUrl, metadata: Map[String, String] = Map(), blob: Option[Blob] = None) extends Story {
		def updateMeta(f: Map[String, String] => Map[String, String]): Asset = copy(metadata = f(metadata))
	}
	final case class Failed(reason: List[String]) extends Story
	final case class Blob(sha1: String, mediatype: String) extends Story

	final case class Description(id: String, name: String, size: Int)
	final case class Content(gallery: Gallery[Asset], chapters: List[(Int, Chapter)])

	object More {
		abstract class Kind(val name: String)
		case object Unused extends Kind("")
		case object Archive extends Kind("archive")
		case object Page extends Kind("page")
		case object Issue extends Kind("chapter")
		case class Other(override val name: String) extends Kind(name)
		object Kind {
			def apply(name: String): Kind = name match {
				case Unused.name => Unused
				case Archive.name => Archive
				case Page.name => Page
				case Issue.name => Issue
				case s => Other(s)
			}
		}
	}

}


