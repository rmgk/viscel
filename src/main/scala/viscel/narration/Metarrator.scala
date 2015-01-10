package viscel.narration

import java.nio.charset.StandardCharsets
import java.nio.file.{NoSuchFileException, Files, Paths}
import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import upickle.{Writer, Reader}
import viscel.{Log, Viscel}
import viscel.shared.ViscelUrl

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.Set

abstract class Metarrator[T<: Narrator : Reader : Writer ](id: String) {

	def archive: ViscelUrl
	def wrap(document: Document): List[T] Or Every[ErrorMessage]

	def path = Paths.get("data", s"$id.json")
	def load(): Set[T] = {
		try {
			val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala
			Viscel.time(s"load $id") { upickle.read[Set[T]](lines.mkString("\n")) }
		}
		catch {
			case e: NoSuchFileException =>
				Log.warn(s"could not load $path")
				Set()
		}
	}
	def save(nars: List[T]): Unit = {
		val json = upickle.write(nars)
		Files.createDirectories(path.getParent)
		Files.write(path, json.getBytes(StandardCharsets.UTF_8))
	}
}
