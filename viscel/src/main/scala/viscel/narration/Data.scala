package viscel.narration

import java.net.URL

import viscel.scribe.narration.Asset

import scala.collection.immutable.Map


object Data {
	def mapToList[T](map: Map[T, T]): List[T] = map.flatMap { case (a, b) => List(a, b) }.toList
	def listToMap[T](data: List[T]): Map[T, T] = data.sliding(2, 2).map(l => (l(0), l(1))).toMap

	def Chapter(name: String): Asset = Asset(kind = AssetKind.chapter, data = List(name), blob = None, origin = None)
	def Article(blob: URL, origin: URL, data: Map[String, String]): Asset = Article(blob, origin, mapToList(data))
	def Article(blob: URL, origin: URL, data: List[String] = Nil): Asset = Asset(kind = AssetKind.article, blob = Some(blob), origin = Some(origin), data = data)
}

object AssetKind {
	val article: Byte = 0
	val chapter: Byte = 1
}