package viscel.narration

import io.circe.{Decoder, Encoder}
import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import viscel.Viscel
import viscel.scribe.Vurl
import viscel.selection.Report
import viscel.shared.Log
import viscel.store.Json

import scala.collection.Set

abstract class Metarrator[T <: Narrator](id: String) {

	def unapply(description: String): Option[Vurl]
	def wrap(document: Document): List[T] Or Every[Report]

	def reader: Decoder[T]
	def writer: Encoder[T]

	private implicit def r: Decoder[T] = reader
	private implicit def w: Encoder[T] = writer

	def path = Viscel.metarratorconfigdir.resolve(s"$id.json")
	def load(): Set[T] = Json.load[Set[T]](path).fold(x => x, err => {
		Log.warn(s"could not load $path: $err")
		Set()
	})
	def save(nars: List[T]): Unit = Json.store(path, nars)

}
