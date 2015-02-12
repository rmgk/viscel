package viscel.store

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import org.scalactic.{Bad, Good, Or}

import scala.Predef.augmentString
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.immutable.Map

object Json {

	def store(p: Path, data: Map[String, Long]) = synchronized {
		val jsonBytes = data.map { case (s, l) => s"$s=$l\n" }.mkString("").getBytes(UTF_8)
		Files.createDirectories(p.getParent)
		Files.write(p, jsonBytes)
	}

	def load(p: Path): Map[String, Long] Or Exception = synchronized {
		val s = Files.lines(p, UTF_8)
		try {
			val res = s.iterator().asScala.map(_.split("=", 2)).map { case Array(id, data) => (id, data.toLong) }.toMap
			Good(res)
		}
		catch { case e: Exception => Bad(e) }
		finally { s.close() }
	}

}
