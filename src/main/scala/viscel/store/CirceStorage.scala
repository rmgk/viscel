package viscel.store

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path}

import io.circe.syntax._
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import viscel.shared.Vid

import scala.jdk.CollectionConverters._

object CirceStorage {

  def store[T: Encoder](p: Path, data: T): Unit = synchronized {
    val jsonBytes = data.asJson.spaces2 :: Nil
    Files.createDirectories(p.getParent)
    Files.write(p, jsonBytes.asJava, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)
  }

  def load[T: Decoder](p: Path): Either[Exception, T] = synchronized {
    try {
      val jsonString = String.join("\n", Files.readAllLines(p, UTF_8))
      io.circe.parser.decode[T](jsonString)
    }
    catch {case e: IOException => Left(e)}
  }


  implicit def vidR: Decoder[Vid] = Decoder.decodeString.map(Vid.from)
  implicit def vidKR: KeyDecoder[Vid] = KeyDecoder.decodeKeyString.map(Vid.from)
  implicit def vidW: Encoder[Vid] = Encoder.encodeString.contramap[Vid](_.str)
  implicit def vidKW: KeyEncoder[Vid] = KeyEncoder.encodeKeyString.contramap[Vid](_.str)

}