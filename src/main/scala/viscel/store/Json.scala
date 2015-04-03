package viscel.store

import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import org.scalactic.{Bad, Good, Or}
import upickle.{Reader, Writer}

object Json {

	implicit val urlReader: Reader[URL] = Reader[URL] {
		case upickle.Js.Str(str) => new URL(str)
	}
	implicit val urlWriter: Writer[URL] = Writer[URL] { url => upickle.Js.Str(url.toString) }

	def store[T: Writer](p: Path, data: T) = synchronized {
		val jsonBytes = upickle.write(data).getBytes(UTF_8)
		Files.createDirectories(p.getParent)
		Files.write(p, jsonBytes)
	}

	def load[T: Reader](p: Path): T Or Exception = synchronized {
		try {
			val jsonString = String.join("\n", Files.readAllLines(p, UTF_8))
			Good(upickle.read[T](jsonString))
		}
		catch {case e: Exception => Bad(e)}
	}

}
