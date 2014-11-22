package viscel.shared

import upickle.{Writer, Js, Reader, writeJs}

case class Gallery[A](left: List[A], right: List[A]) {
	def toList: List[A] = left reverse_::: right
	def first: Gallery[A] = Gallery(Nil, toList)
	def end: Gallery[A] = Gallery(right reverse_:::left, Nil)
	def get: Option[A] = right.headOption
	def next(n: Int): Gallery[A] = Gallery(right.take(n) reverse_::: left, right.drop(n))
	def prev(n: Int): Gallery[A] = Gallery(left.drop(n), left.take(n) reverse_::: right)
	override def toString: String = s"Gallery(${left.reverse.mkString(", ")} | ${right.mkString(", ")})"
}

object Gallery {
	def fromList[A](l: List[A]): Gallery[A] = Gallery(Nil, l)
	implicit def galleryR[A: Reader]: Reader[Gallery[A]] = Reader[Gallery[A]](upickle.SeqishR[A, List].read.andThen(fromList))
	implicit def galleryW[A: Writer]: Writer[Gallery[A]] = Writer[Gallery[A]](g => writeJs(g.toList))
}
