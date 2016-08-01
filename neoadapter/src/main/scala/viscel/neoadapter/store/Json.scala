package viscel.neoadapter.store

import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.Instant

import org.scalactic.{Bad, Good, Or}
import upickle.Js
import upickle.default.{Reader, Writer, readJs, writeJs}

import scala.collection.immutable.Map

object Json {

	implicit val urlReader: Reader[URL] = Reader[URL] {
		case upickle.Js.Str(str) => new URL(str)
	}
	implicit val urlWriter: Writer[URL] = Writer[URL] { url => upickle.Js.Str(url.toString) }

	implicit val instantWriter: Writer[Instant] = Writer[Instant] { instant =>
		upickle.Js.Str(instant.toString)
	}

	implicit val instantReader: Reader[Instant] = Reader[Instant] {
		case upickle.Js.Str(str) => Instant.parse(str)
	}

//	implicit def stringMapR[V: Reader]: Reader[Map[String, V]] = Reader[Map[String, V]] {
//		case Js.Obj(kv@_*) => kv.map { case (k, jsv) => k -> readJs[V](jsv) }.toMap
//	}
//	implicit def stringMapW[V: Writer]: Writer[Map[String, V]] = Writer[Map[String, V]] { m =>
//		Js.Obj(m.mapValues(writeJs[V]).toSeq: _*)
//	}

	def store[T: Writer](p: Path, data: T) = synchronized {
		val jsonBytes = upickle.default.write(data).getBytes(UTF_8)
		Files.createDirectories(p.getParent)
		Files.write(p, jsonBytes)
	}

	def load[T: Reader](p: Path): T Or Exception = synchronized {
		try {
			val jsonString = String.join("\n", Files.readAllLines(p, UTF_8))
			Good(upickle.default.read[T](jsonString))
		}
		catch {case e: Exception => Bad(e)}
	}

}
