package viscel.narration

import java.net.URL

import viscel.scribe.narration.{Article}

import scala.collection.immutable.Map


object Data {
	def mapToList[T](map: Map[T, T]): List[T] = map.flatMap { case (a, b) => List(a, b) }.toList
	def listToMap[T](data: List[T]): Map[T, T] = data.sliding(2, 2).map(l => (l(0), l(1))).toMap

	def Chapter(name: String): viscel.scribe.narration.Chapter = viscel.scribe.narration.Chapter(name)
	def Article(blob: URL, origin: URL, data: Map[String, String]): Article = Article(blob, origin, mapToList(data))
	def Article(blob: URL, origin: URL, data: List[String] = Nil): Article = viscel.scribe.narration.Article( blob = blob, origin = Some(origin), data = data)
}
