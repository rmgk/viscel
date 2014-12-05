package viscel.shared

import upickle.{Reader, Writer, writeJs}

case class Gallery[A](left: List[A], right: List[A]) {
	lazy val toList: List[A] = left reverse_::: right
	lazy val first: Gallery[A] = Gallery(Nil, toList)
	lazy val end: Gallery[A] = Gallery(right reverse_::: left, Nil)
	def get: Option[A] = right.headOption
	def next(n: Int): Gallery[A] = Gallery(right.take(n) reverse_::: left, right.drop(n))
	def prev(n: Int): Gallery[A] = Gallery(left.drop(n), left.take(n) reverse_::: right)
	lazy val size: Int = pos + right.size
	lazy val pos: Int = left.size
	override lazy val toString: String = s"Gallery(${left.reverse.mkString(", ")} | ${right.mkString(", ")})"
	def isFirst: Boolean = left.isEmpty
	def isEnd: Boolean = right.isEmpty
	def isEmpty: Boolean = isFirst && isEnd
}

object Gallery {
	def fromList[A](l: List[A]): Gallery[A] = Gallery(Nil, l)
	implicit def galleryR[A: Reader]: Reader[Gallery[A]] = Reader[Gallery[A]](upickle.SeqishR[A, List].read.andThen(fromList))
	implicit def galleryW[A: Writer]: Writer[Gallery[A]] = Writer[Gallery[A]](g => writeJs(g.toList))
}
