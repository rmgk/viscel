package viscel.narration

import java.nio.file.Paths

import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import upickle.{Reader, Writer}
import viscel.Log
import viscel.shared.ViscelUrl
import viscel.store.Json

import scala.collection.Set

abstract class Metarrator[T <: Narrator : Reader : Writer](id: String) {

	def unapply(vurl: ViscelUrl): Option[ViscelUrl]
	def wrap(document: Document): List[T] Or Every[ErrorMessage]

	def path = Paths.get("data", s"$id.json")
	def load(): Set[T] = Json.load[Set[T]](path).fold(x => x, err => {
		Log.warn(s"could not load $path: $err")
		Set()
	})
	def save(nars: List[T]): Unit = Json.store(path, nars)

}
