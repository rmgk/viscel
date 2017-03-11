package viscel.scribe

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path}

import org.scalactic.{Bad, Good, Or}
import upickle.default.{Reader, Writer}

import scala.collection.JavaConverters._

object Json {

	def store[T: Writer](p: Path, data: T): Unit = synchronized {
		val jsonBytes = upickle.default.write(data) :: Nil
		Files.createDirectories(p.getParent)
		Files.write(p, jsonBytes.asJava, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)
	}

	def load[T: Reader](p: Path): T Or Exception = synchronized {
		try {
			val jsonString = String.join("\n", Files.readAllLines(p, UTF_8))
			Good(upickle.default.read[T](jsonString))
		}
		catch {case e: Exception => Bad(e)}
	}

}
