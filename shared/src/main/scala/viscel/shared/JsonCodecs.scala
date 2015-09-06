package viscel.shared

import upickle.Js.Value
import upickle.Js
import upickle.default.{Reader => R, Writer => W, readJs, writeJs}

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Map

object JsonCodecs {

	implicit def stringMapR[V: R]: R[Map[String, V]] = R[Map[String, V]] {
		case Js.Obj(kv@_*) => kv.map { case (k, jsv) => k -> readJs[V](jsv) }.toMap
	}
	implicit def stringMapW[V: W]: W[Map[String, V]] = W[Map[String, V]] { m =>
		Js.Obj(m.mapValues(writeJs[V]).toSeq: _*)
	}

}

case class ReaderWriter[T](reader: R[T], writer: W[T]) extends R[T] with W[T] {
	override def read0: PartialFunction[Value, T] = reader.read
	override def write0: (T) => Value = writer.write
}
