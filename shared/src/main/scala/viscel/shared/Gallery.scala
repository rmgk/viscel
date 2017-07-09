package viscel.shared

import scala.reflect.ClassTag
import io.circe.{Encoder, Decoder}


final class Gallery[+A] private(val pos: Int, entries: Array[A]) {
	private def copy(position: Int) = new Gallery(position, entries)
	def toList: List[A] = List(entries: _*)
	def toSeq: Seq[A] = genericWrapArray(entries)
	def first: Gallery[A] = copy(0)
	def end: Gallery[A] = copy(entries.length)
	def get: Option[A] = if (pos < size) Some(entries(pos)) else None
	def next(n: Int): Gallery[A] = copy(if (pos + n < size) pos + n else size)
	def prev(n: Int): Gallery[A] = copy(if (pos - n >= 0) pos - n else 0)
	def size: Int = entries.length
	override def toString: String = s"Gallery(${Predef.genericWrapArray(entries).mkString(", ")})"
	def isFirst: Boolean = pos == 0
	def isEnd: Boolean = pos == size
	def isEmpty: Boolean = size == 0
}

object Gallery {
	def fromArray[A](a: Array[A]): Gallery[A] = new Gallery(0, a)
	def fromSeq(seq: Seq[ImageRef]): Gallery[ImageRef] = fromArray(seq.toArray)
	def empty[A]: Gallery[A] = fromArray(Array())
	implicit def galleryR[A: Decoder : ClassTag]: Decoder[Gallery[A]] = implicitly[Decoder[Array[A]]].map(fromArray)
	implicit def galleryW[A: Encoder : ClassTag]: Encoder[Gallery[A]] = implicitly[Encoder[Array[A]]].contramap[Gallery[A]](_.toSeq.toArray)
}
