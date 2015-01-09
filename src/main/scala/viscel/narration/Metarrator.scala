package viscel.narration

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import upickle.{Writer, Reader}
import viscel.Viscel
import viscel.shared.AbsUri

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.Set

abstract class Metarrator[T<: Narrator : Reader : Writer ](id: String) {

	def archive: AbsUri
	def wrap(document: Document): List[T] Or Every[ErrorMessage]

	def path = Paths.get("data", s"$id.json")
	def load(): Set[T] = {
		val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala
		Viscel.time(s"load $id") { upickle.read[Set[T]](lines.mkString("\n")) }
	}
	def save(nars: List[T]): Unit = {
		val json = upickle.write(nars)
		Files.createDirectories(path.getParent)
		Files.write(path, json.getBytes(StandardCharsets.UTF_8))
	}
}
