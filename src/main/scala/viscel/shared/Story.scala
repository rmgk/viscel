package viscel.shared

import upickle.{Js, Reader, Writer, readJs, writeJs}

import scala.collection.immutable.Map
import scala.language.implicitConversions

sealed trait Story

object Story {
	import viscel.shared.JsonCodecs._

	final case class More(loc: AbsUri, pagetype: String, layer: List[Story] = Nil) extends Story
	final case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Story
	final case class Asset(source: AbsUri, origin: AbsUri, metadata: Map[String, String] = Map(), blob: Option[Blob] = None) extends Story
	final case class Core(kind: String, id: String, name: String, metadata: Map[String, String]) extends Story
	final case class Failed(reason: List[String]) extends Story
	final case class Blob(sha1: String, mediatype: String) extends Story
	final case class Narration(id: String, name: String, size: Int, narrates: List[Asset])

	implicit val (moreR, moreW): (Reader[More], Writer[More]) = case3RW("loc", "pagetype", "layer", More.apply, More.unapply)
	implicit val (chapterR, chapterW): (Reader[Chapter], Writer[Chapter]) = case2RW("name", "metadata", Chapter.apply, Chapter.unapply)
	implicit val (blobR, blobW): (Reader[Blob], Writer[Blob]) = case2RW("sha1", "mediatype", Blob.apply, Blob.unapply)
	implicit val (assetR, assetW): (Reader[Asset], Writer[Asset]) = case4RW("source", "origin", "metadata", "blob", Asset.apply, Asset.unapply)
	implicit val (coreR, coreW): (Reader[Core], Writer[Core]) = case4RW("kind", "id", "name", "metadata", Core.apply, Core.unapply)
	implicit val (failedR, failedW): (Reader[Failed], Writer[Failed]) = case1RW("reason", Failed.apply, Failed.unapply)
	implicit val (narrationR, narrationW): (Reader[Narration], Writer[Narration]) = case4RW("id", "name", "size", "narrates", Narration.apply, Narration.unapply)

	implicit val storyWriter: Writer[Story] = Writer[Story] {
		case s @ More(_, _, _) => writeJs(("More", s))
		case s @ Chapter(_, _) => writeJs(("Chapter", s))
		case s @ Asset(_, _, _, _) => writeJs(("Asset", s))
		case s @ Core(_, _, _, _) => writeJs(("Core", s))
		case s @ Failed(_) => writeJs(("Failed", s))
		case s @ Blob(_, _) => writeJs(("Blob", s))
	}
	implicit val storyReader: Reader[Story] = Reader[Story] {
		case Js.Arr(Js.Str("More"), s @ Js.Obj(_*)) => readJs[More](s)
		case Js.Arr(Js.Str("Chapter"), s @ Js.Obj(_*)) => readJs[Chapter](s)
		case Js.Arr(Js.Str("Asset"), s @ Js.Obj(_*)) => readJs[Asset](s)
		case Js.Arr(Js.Str("Core"), s @ Js.Obj(_*)) => readJs[Core](s)
		case Js.Arr(Js.Str("Failed"), s @ Js.Obj(_*)) => readJs[Failed](s)
		case Js.Arr(Js.Str("Blob"), s @ Js.Obj(_*)) => readJs[Blob](s)
	}

}


