package viscel.shared

import scala.collection.immutable.Map
import scala.language.implicitConversions
import upickle.{Writer, Reader, Js, key}
import scala.Predef.any2ArrowAssoc
import scala.Predef.implicitly
import upickle._

sealed trait Story

object Story {
	import viscel.shared.ReadWriteUtil._

	final case class More(loc: AbsUri, pagetype: String, layer: List[Story] = Nil) extends Story
	final case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Story
	final case class Asset(source: AbsUri, origin: AbsUri, metadata: Map[String, String] = Map(), blob: Option[Blob] = None) extends Story
	final case class Core(kind: String, id: String, name: String, metadata: Map[String, String]) extends Story
	final case class Failed(reason: List[String]) extends Story
	final case class Narration(id: String, name: String, size: Int, narrates: List[Asset])
	final case class Blob(sha1: String, mediatype: String)

	implicit val (moreR, moreW): (Reader[More], Writer[More]) = case3ReadWrite("loc", "pagetype", "layer", More.apply, More.unapply)
	implicit val (chapterR, chapterW): (Reader[Chapter], Writer[Chapter]) = case2ReadWrite("name", "metadata", Chapter.apply, Chapter.unapply)
	implicit val (blobR, blobW): (Reader[Blob], Writer[Blob]) = case2ReadWrite("sha1", "mediatype", Blob.apply, Blob.unapply)
	implicit val (assetR, assetW): (Reader[Asset], Writer[Asset]) = case4ReadWrite("source", "origin", "metadata", "blob", Asset.apply, Asset.unapply)
	implicit val (coreR, coreW): (Reader[Core], Writer[Core]) = case4ReadWrite("kind", "id", "name", "metadata", Core.apply, Core.unapply)
	implicit val (failedR, failedW): (Reader[Failed], Writer[Failed]) = case1ReadWrite("reason", Failed.apply, Failed.unapply)
	implicit val (narrationR, narrationW): (Reader[Narration], Writer[Narration]) = case4ReadWrite("id", "name", "size", "narrates", Narration.apply, Narration.unapply)



	implicit val storyWriter: Writer[Story] = Writer[Story] {
		case s @ More(_, _, _) => writeJs(("More", s))
		case s @ Chapter(_, _) => writeJs(("Chapter", s))
		case s @ Asset(_, _, _, _) => writeJs(("Asset", s))
		case s @ Core(_, _, _, _) => writeJs(("Core", s))
		case s @ Failed(_) => writeJs(("Failed", s))
	}
	implicit val storyReader: Reader[Story] = Reader[Story] {
		case Js.Arr(Js.Str("More"), s @ Js.Obj(_*)) => readJs[More](s)
		case Js.Arr(Js.Str("Chapter"), s @ Js.Obj(_*)) => readJs[Chapter](s)
		case Js.Arr(Js.Str("Asset"), s @ Js.Obj(_*)) => readJs[Asset](s)
		case Js.Arr(Js.Str("Core"), s @ Js.Obj(_*)) => readJs[Core](s)
		case Js.Arr(Js.Str("Failed"), s @ Js.Obj(_*)) => readJs[Failed](s)
	}

	implicitly[Reader[List[Narration]]](SeqishR[Narration,List])
	implicitly[Reader[Story]]

}

object ReadWriteUtil {
	type R[X] = Reader[X]
	type W[X] = Writer[X]

	def case1ReadWrite[A:R:W, T](n1: String, w: (A) => T, r: T => Option[A]): (Reader[T], Writer[T]) = {
		(case1Reader(n1, w), case1Writer(n1,r(_).get))
	}
	def case2ReadWrite[A:R:W, B:R:W, T](n1: String, n2: String, w: (A, B) => T, r: T => Option[(A, B)]): (Reader[T], Writer[T]) = {
		(case2Reader(n1,n2, w), case2Writer(n1,n2,r(_).get))
	}
	def case3ReadWrite[A:R:W, B:R:W, C:R:W, T](n1: String, n2: String, n3: String, w: (A, B, C) => T, r: T => Option[(A, B, C)]): (Reader[T], Writer[T]) = {
		(case3Reader(n1,n2,n3, w), case3Writer(n1,n2,n3,r(_).get))
	}
	def case4ReadWrite[A:R:W, B:R:W, C:R:W, D:R:W, T](n1: String, n2: String, n3: String, n4: String, w: (A, B, C, D) => T, r: T => Option[(A, B, C, D)]): (Reader[T], Writer[T]) = {
		(case4Reader(n1,n2,n3,n4, w), case4Writer(n1,n2,n3,n4,r(_).get))
	}

	def case1Writer[A: Writer, T](n1: String, f: T => A): Writer[T] = Writer[T] { t: T =>
		val a = f(t)
		Js.Obj(n1 -> writeJs(a))
	}
	def case2Writer[A: Writer, B: Writer, T](n1: String, n2: String, f: T => (A, B)): Writer[T] = Writer[T] { t: T =>
		val a = f(t)
		Js.Obj(n1 -> writeJs(a._1), n2 -> writeJs(a._2))
	}
	def case3Writer[A: Writer, B: Writer, C: Writer, T](n1: String, n2: String, n3: String, f: T => (A, B, C)): Writer[T] = Writer[T] { t: T =>
		val a = f(t)
		Js.Obj(n1 -> writeJs(a._1), n2 -> writeJs(a._2), n3 -> writeJs(a._3))
	}
	def case4Writer[A: Writer, B: Writer, C: Writer, D: Writer, T](n1: String, n2: String, n3: String, n4: String, f: T => (A, B, C, D)): Writer[T] = Writer[T] { t: T =>
		val a = f(t)
		Js.Obj(n1 -> writeJs(a._1), n2 -> writeJs(a._2), n3 -> writeJs(a._3), n4 -> writeJs(a._4))
	}

	def case1Reader[A: Reader, T](n1: String, f: (A) => T): Reader[T] = Reader[T] {
		case Js.Obj((`n1`, a1)) => f(readJs[A](a1))
	}
	def case2Reader[A: Reader, B: Reader, T](n1: String, n2: String, f: (A, B) => T): Reader[T] = Reader[T] {
		case Js.Obj((`n1`, a1), (`n2`, a2)) => f(readJs[A](a1), readJs[B](a2))
	}
	def case3Reader[A: Reader, B: Reader, C: Reader, T](n1: String, n2: String, n3: String, f: (A, B, C) => T): Reader[T] = Reader[T] {
		case Js.Obj((`n1`, a1), (`n2`, a2), (`n3`, a3)) => f(readJs[A](a1), readJs[B](a2), readJs[C](a3))
	}
	def case4Reader[A: Reader, B: Reader, C: Reader, D: Reader, T](n1: String, n2: String, n3: String, n4: String, f: (A, B, C, D) => T): Reader[T] = Reader[T] {
		case Js.Obj((`n1`, a1), (`n2`, a2), (`n3`, a3), (`n4`, a4)) => f(readJs[A](a1), readJs[B](a2), readJs[C](a3), readJs[D](a4))
	}
}
