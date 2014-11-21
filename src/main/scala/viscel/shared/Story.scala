package viscel.shared

import scala.collection.immutable.Map
import scala.language.implicitConversions
import upickle.{Writer, Reader, Js, key}
import scala.Predef.any2ArrowAssoc
import scala.Predef.implicitly
import upickle._

sealed trait Story

object Story {
	@key("More") final case class More(loc: AbsUri, pagetype: String, narration: List[Story] = Nil) extends Story
	@key("Chapter") final case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Story
	@key("Asset") final case class Asset(source: AbsUri, origin: AbsUri, metadata: Map[String, String] = Map(), blob: Option[Blob] = None) extends Story
	@key("Core") final case class Core(kind: String, id: String, name: String, metadata: Map[String, String]) extends Story
	@key("Failed") final case class Failed(reason: List[String]) extends Story

	object More {
		implicit lazy val moreReader: upickle.Reader[More] = upickle.Reader[More]{
			case Js.Obj(("loc", absuri), ("pagetype", Js.Str(pagetype)), ("narration", Js.Arr(stories @ _*))) =>
				val uri = implicitly[Reader[AbsUri]].read(absuri)
				val narration = stories.map(s => implicitly[Reader[Story]].read(s)).toList
				More(uri, pagetype, narration)
		}
	}

	implicit lazy val storyReader: upickle.Reader[Story] = upickle.Reader[Story] {
		case Js.Arr(Js.Str("More"), s @ Js.Obj(_)) => implicitly[Reader[More]].read(s)
		case Js.Arr(Js.Str("Chapter"), s @ Js.Obj(_)) => implicitly[Reader[Chapter]].read(s)
		case Js.Arr(Js.Str("Asset"), s @ Js.Obj(_)) => implicitly[Reader[Asset]].read(s)
		case Js.Arr(Js.Str("Core"), s @ Js.Obj(_)) => implicitly[Reader[Core]].read(s)
		case Js.Arr(Js.Str("Failed"), s @ Js.Obj(_)) => implicitly[Reader[Failed]].read(s)
	}

	@key("Narration") final case class Narration(id: String, name: String, size: Int, narration: List[Narration])

	@key("Blob") final case class Blob(sha1: String, mediatype: String)
}
