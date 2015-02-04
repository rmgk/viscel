package viscel.shared

import scala.collection.immutable.Map
import scala.language.implicitConversions

sealed trait Story

object Story {

	import viscel.shared.JsonCodecs._

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
		implicit val kindR: upickle.Reader[Kind] = upickle.Reader(PartialFunction(n => Kind(upickle.readJs[String](n))))
		implicit val kindW: upickle.Writer[Kind]= upickle.Writer(k => upickle.writeJs(k.name))
	}

	implicit val moreR: ReaderWriter[More] = case2RW(More.apply, More.unapply)("loc", "pagetype")
	implicit val chapterR: ReaderWriter[Chapter] = case2RW(Chapter.apply, Chapter.unapply)("name", "metadata")
	implicit val blobR: ReaderWriter[Blob] = case2RW(Blob.apply, Blob.unapply)("sha1", "mediatype")
	implicit val assetR: ReaderWriter[Asset] = case4RW(Asset.apply, Asset.unapply)("source", "origin", "metadata", "blob")
	implicit val failedR: ReaderWriter[Failed] = case1RW(Failed.apply, Failed.unapply)("reason")
	implicit val descriptionRW: ReaderWriter[Description] = case3RW(Description.apply, Description.unapply)("id", "name", "size")
	implicit val contentRW: ReaderWriter[Content] = case2RW(Content.apply, Content.unapply)("gallery", "chapters")

	//	implicit val storyWriter: Writer[Story] = Writer[Story] {
	//		case s @ More(_, _) => writeJs(("More", s))
	//		case s @ Chapter(_, _) => writeJs(("Chapter", s))
	//		case s @ Asset(_, _, _, _) => writeJs(("Asset", s))
	//		case s @ Core(_, _, _, _) => writeJs(("Core", s))
	//		case s @ Failed(_) => writeJs(("Failed", s))
	//		case s @ Blob(_, _) => writeJs(("Blob", s))
	//	}
	//	implicit val storyReader: Reader[Story] = Reader[Story] {
	//		case Js.Arr(Js.Str("More"), s @ Js.Obj(_*)) => readJs[More](s)
	//		case Js.Arr(Js.Str("Chapter"), s @ Js.Obj(_*)) => readJs[Chapter](s)
	//		case Js.Arr(Js.Str("Asset"), s @ Js.Obj(_*)) => readJs[Asset](s)
	//		case Js.Arr(Js.Str("Core"), s @ Js.Obj(_*)) => readJs[Core](s)
	//		case Js.Arr(Js.Str("Failed"), s @ Js.Obj(_*)) => readJs[Failed](s)
	//		case Js.Arr(Js.Str("Blob"), s @ Js.Obj(_*)) => readJs[Blob](s)
	//	}

}


