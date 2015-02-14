package viscel.narration

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import upickle.{Reader, Writer}
import viscel.shared.ViscelUrl
import viscel.store.Json
import viscel.{Log, Viscel}

import scala.collection.Set

abstract class Metarrator[T <: Narrator : Reader : Writer](id: String) {

	def unapply(description: String): Option[URL]
	def wrap(document: Document): List[T] Or Every[ErrorMessage]

	def path = Viscel.basepath.resolve("data").resolve(s"$id.json")
	def load(): Set[T] = Json.load[Set[T]](path).fold(x => x, err => {
		Log.warn(s"could not load $path: $err")
		Set()
	})
	def save(nars: List[T]): Unit = Json.store(path, nars)

}
