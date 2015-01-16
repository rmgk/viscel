package viscel.shared

import upickle.{Reader, Writer, writeJs}

import scala.collection.mutable
import scala.reflect.ClassTag


final class Gallery[+A] private (val pos: Int, entries: Array[A]) {
	def copy(position: Int) = new Gallery(position, entries)
	def toList: List[A] = List(entries: _*)
	def first: Gallery[A] = copy(0)
	def end: Gallery[A] = copy(entries.length)
	def get: Option[A] = if (pos < size) Some(entries(pos)) else None
	def next(n: Int): Gallery[A] = copy(if (pos + n < size) pos + n else size)
	def prev(n: Int): Gallery[A] = copy(if (pos - n  >= 0) pos - n else 0)
	def size: Int = entries.length
	override def toString: String = s"Gallery(${ Predef.genericWrapArray(entries).mkString(", ") })"
	def isFirst: Boolean = pos == 0
	def isEnd: Boolean = pos == size
	def isEmpty: Boolean = isFirst && isEnd
}

object Gallery {
	def fromList[A: ClassTag](l: List[A]): Gallery[A] = fromArray(l.toArray)
	def fromArray[A](a: Array[A]): Gallery[A] = new Gallery(0, a)
	val empty: Gallery[Nothing] = Gallery.fromList(Nil)
	implicit def galleryR[A: Reader : ClassTag]: Reader[Gallery[A]] = Reader[Gallery[A]](upickle.SeqishR[A, mutable.WrappedArray].read.andThen(w => fromArray(w.toArray)))
	implicit def galleryW[A: Writer]: Writer[Gallery[A]] = Writer[Gallery[A]](g => writeJs(g.toList))
}
