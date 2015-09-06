package viscel.narration

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import upickle.default.{Reader, Writer, ReadWriter}
import viscel.scribe.narration.Narrator
import viscel.selection.Report
import viscel.store.Json
import viscel.{Log, Viscel}

import scala.collection.Set

abstract class Metarrator[T <: Narrator](id: String) {

	def unapply(description: String): Option[URL]
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
