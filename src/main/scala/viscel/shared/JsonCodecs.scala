package viscel.shared

import upickle.{Js, Reader, Writer, writeJs, readJs}
import scala.Predef.any2ArrowAssoc
import scala.collection.immutable.Map

object JsonCodecs {
	type R[X] = Reader[X]
	type W[X] = Writer[X]

	implicit def stringMapReader[V: Reader]: Reader[Map[String, V]] = Reader[Map[String, V]] {
		case Js.Obj(kv @ _*) => kv.map{case (k, jsv) => k -> readJs[V](jsv)}.toMap
	}
	implicit def stringMapWriter[V: Writer]: Writer[Map[String, V]] = Writer[Map[String, V]] { m =>
		Js.Obj(m.mapValues(writeJs[V]).toSeq: _*)
	}

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
