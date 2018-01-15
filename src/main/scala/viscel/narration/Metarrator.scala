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

abstract class Metarrator[T <: Narrator](val id: String, val decoder: Decoder[T], val encoder: Encoder[T]) {

	def unapply(description: String): Option[Vurl]
	def wrap(document: Document): List[T] Or Every[Report]

	private def path = Viscel.services.metarratorconfigdir.resolve(s"$id.json")
	def load(): Set[T] = {
		val json = Json.load[Set[T]](path)(io.circe.Decoder.decodeTraversable(decoder, implicitly))
			json.fold(x => x, err => {
			Log.warn(s"could not load $path: $err")
			Set()
		})
	}
	def save(nars: List[T]): Unit = Json.store(path, nars)(io.circe.Encoder.encodeList(encoder))

}
