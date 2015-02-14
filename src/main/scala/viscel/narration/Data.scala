package viscel.narration

import java.net.URL

import viscel.scribe.narration.Asset

import scala.collection.immutable.Map


object Data {
	def mapToList[T](map: Map[T, T]): List[T] = map.flatMap { case (a, b) => List(a, b) }.toList

	def chapter(name: String, data: Map[String, String]): Asset = chapter(name, mapToList(data))
	def chapter(name: String, data: List[String]): Asset = Asset(kind = Kind.chapter, data = List(name), blob = None, origin = None)
	def article(blob: URL, origin: URL, data: Map[String, String]): Asset = article(blob, origin, mapToList(data))
	def article(blob: URL, origin: URL, data: List[String]): Asset = Asset(kind = Kind.article, blob = Some(blob), origin = Some(origin), data = data)
}

object Kind {
	val article: Byte = 0
	val chapter: Byte = 1
}