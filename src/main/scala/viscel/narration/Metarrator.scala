package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import upickle.default.{ReadWriter, Reader, Writer}
import viscel.Viscel
import viscel.scribe.{Json, Vurl}
import viscel.selection.Report
import viscel.shared.Log

import scala.collection.Set

abstract class Metarrator[T <: Narrator](id: String) {

	def unapply(description: String): Option[Vurl]
	def wrap(document: Document): List[T] Or Every[Report]

	def reader: Reader[T]
	def writer: Writer[T]

	private implicit def rw: ReadWriter[T] = ReadWriter(writer.write, reader.read)

	def path = Viscel.basepath.resolve("data").resolve(s"$id.json")
	def load(): Set[T] = Json.load[Set[T]](path).fold(x => x, err => {
		Log.warn(s"could not load $path: $err")
		Set()
	})
	def save(nars: List[T]): Unit = Json.store(path, nars)

}
