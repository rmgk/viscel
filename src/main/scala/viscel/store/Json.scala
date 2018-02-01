package viscel.store

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path}

import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalactic.{Bad, Or}

import scala.collection.JavaConverters._

object Json {

  def store[T: Encoder](p: Path, data: T): Unit = synchronized {
    val jsonBytes = data.asJson.noSpaces :: Nil
    Files.createDirectories(p.getParent)
    Files.write(p, jsonBytes.asJava, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)
  }

  def load[T: Decoder](p: Path): T Or Exception = synchronized {
    try {
      val jsonString = String.join("\n", Files.readAllLines(p, UTF_8))
      Or.from(io.circe.parser.decode[T](jsonString))
    }
    catch {case e: Exception => Bad(e)}
  }

}
