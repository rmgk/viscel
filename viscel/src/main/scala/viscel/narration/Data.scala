package viscel.narration

import scala.collection.immutable.Map


object Data {
	def mapToList[T](map: Map[T, T]): List[T] = map.flatMap { case (a, b) => List(a, b) }.toList
	def listToMap[T](data: List[T]): Map[T, T] = data.sliding(2, 2).map(l => (l(0), l(1))).toMap
}
