package viscel.shared

import upickle.{Js, Reader => R, Writer => W, writeJs, readJs}
import scala.Predef.any2ArrowAssoc
import scala.collection.immutable.Map

object JsonCodecs {

	implicit def stringMapR[V: R]: R[Map[String, V]] = R[Map[String, V]] {
		case Js.Obj(kv @ _*) => kv.map{case (k, jsv) => k -> readJs[V](jsv)}.toMap
	}
	implicit def stringMapW[V: W]: W[Map[String, V]] = W[Map[String, V]] { m =>
		Js.Obj(m.mapValues(writeJs[V]).toSeq: _*)
	}

	def case1RW[A:R:W, T](n1: String, w: (A) => T, r: T => Option[A]): (R[T], W[T]) = {
		(case1R(n1, w), case1W(n1,r(_).get))
	}
	def case2RW[A:R:W, B:R:W, T](n1: String, n2: String, w: (A, B) => T, r: T => Option[(A, B)]): (R[T], W[T]) = {
		(case2R(n1,n2, w), case2W(n1,n2,r(_).get))
	}
	def case3RW[A:R:W, B:R:W, C:R:W, T](n1: String, n2: String, n3: String, w: (A, B, C) => T, r: T => Option[(A, B, C)]): (R[T], W[T]) = {
		(case3R(n1,n2,n3, w), case3W(n1,n2,n3,r(_).get))
	}
	def case4RW[A:R:W, B:R:W, C:R:W, D:R:W, T](n1: String, n2: String, n3: String, n4: String, w: (A, B, C, D) => T, r: T => Option[(A, B, C, D)]): (R[T], W[T]) = {
		(case4R(n1,n2,n3,n4, w), case4W(n1,n2,n3,n4,r(_).get))
	}

	def case1W[A: W, T](n1: String, f: T => A): W[T] = W[T] { t: T =>
		val a = f(t)
		Js.Obj(n1 -> writeJs(a))
	}
	def case2W[A: W, B: W, T](n1: String, n2: String, f: T => (A, B)): W[T] = W[T] { t: T =>
		val a = f(t)
		Js.Obj(n1 -> writeJs(a._1), n2 -> writeJs(a._2))
	}
	def case3W[A: W, B: W, C: W, T](n1: String, n2: String, n3: String, f: T => (A, B, C)): W[T] = W[T] { t: T =>
		val a = f(t)
		Js.Obj(n1 -> writeJs(a._1), n2 -> writeJs(a._2), n3 -> writeJs(a._3))
	}
	def case4W[A: W, B: W, C: W, D: W, T](n1: String, n2: String, n3: String, n4: String, f: T => (A, B, C, D)): W[T] = W[T] { t: T =>
		val a = f(t)
		Js.Obj(n1 -> writeJs(a._1), n2 -> writeJs(a._2), n3 -> writeJs(a._3), n4 -> writeJs(a._4))
	}

	def case1R[A: R, T](n1: String, f: (A) => T): R[T] = R[T] {
		case Js.Obj((`n1`, a1)) => f(readJs[A](a1))
	}
	def case2R[A: R, B: R, T](n1: String, n2: String, f: (A, B) => T): R[T] = R[T] {
		case Js.Obj((`n1`, a1), (`n2`, a2)) => f(readJs[A](a1), readJs[B](a2))
	}
	def case3R[A: R, B: R, C: R, T](n1: String, n2: String, n3: String, f: (A, B, C) => T): R[T] = R[T] {
		case Js.Obj((`n1`, a1), (`n2`, a2), (`n3`, a3)) => f(readJs[A](a1), readJs[B](a2), readJs[C](a3))
	}
	def case4R[A: R, B: R, C: R, D: R, T](n1: String, n2: String, n3: String, n4: String, f: (A, B, C, D) => T): R[T] = R[T] {
		case Js.Obj((`n1`, a1), (`n2`, a2), (`n3`, a3), (`n4`, a4)) => f(readJs[A](a1), readJs[B](a2), readJs[C](a3), readJs[D](a4))
	}
}
